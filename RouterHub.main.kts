#!/usr/bin/env kotlin

@file:Suppress("FunctionName")

import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * RouterHub - Standalone CLI Network & Router Scanner
 *
 * Scans the local subnet for active web interfaces, routers, and access points.
 * Usage:
 *   kotlin RouterHub.main.kts [subnet-prefix]
 *   e.g. kotlin RouterHub.main.kts 192.168.1
 */

data class DiscoveredHost(
    val ip: String,
    val openPorts: List<Int>,
    val serverBanner: String? = null,
    val isProbableGateway: Boolean = false
)

fun detectLocalIp(): String? {
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("8.8.8.8", 53), 1000)
            (socket.localAddress as? Inet4Address)?.hostAddress
        }
    } catch (_: Exception) {
        try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filter { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .map { it.hostAddress }
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }
}

fun probePort(ip: String, port: Int, timeoutMs: Int = 300): Boolean {
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }
}

fun fetchHttpBanner(ip: String, port: Int, timeoutMs: Int = 600): String? {
    val scheme = if (port == 443) "https" else "http"
    val portSuffix = if (port == 80 || port == 443) "" else ":$port"
    val urlString = "$scheme://$ip$portSuffix/"

    return try {
        val url = URI(urlString).toURL()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", "RouterHubScanner/1.0")
        }
        val server = conn.getHeaderField("Server")
        val authRealm = conn.getHeaderField("WWW-Authenticate")
        conn.disconnect()

        when {
            !server.isNullOrBlank() -> server.trim()
            !authRealm.isNullOrBlank() -> authRealm.trim()
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

fun scanSubnet(
    subnet: String,
    gatewayIp: String,
    portsToScan: List<Int> = listOf(80, 443, 8080),
    threadPoolSize: Int = 64
): List<DiscoveredHost> {
    val pool = Executors.newFixedThreadPool(threadPoolSize)
    val discovered = ConcurrentLinkedQueue<DiscoveredHost>()
    val scannedCount = AtomicInteger(0)
    val totalHosts = 254

    for (hostNum in 1..totalHosts) {
        val ip = "$subnet.$hostNum"
        pool.execute {
            val openPorts = mutableListOf<Int>()
            for (port in portsToScan) {
                if (probePort(ip, port)) {
                    openPorts.add(port)
                }
            }

            if (openPorts.isNotEmpty()) {
                val banner = openPorts.firstNotNullOfOrNull { port -> fetchHttpBanner(ip, port) }
                discovered.add(
                    DiscoveredHost(
                        ip = ip,
                        openPorts = openPorts,
                        serverBanner = banner,
                        isProbableGateway = (ip == gatewayIp)
                    )
                )
            }

            val count = scannedCount.incrementAndGet()
            if (count % 25 == 0 || count == totalHosts) {
                val percent = (count * 100) / totalHosts
                print("\rProgress: $percent% ($count/$totalHosts hosts scanned)")
                System.out.flush()
            }
        }
    }

    pool.shutdown()
    pool.awaitTermination(60, TimeUnit.SECONDS)
    println()

    return discovered.sortedWith(
        compareByDescending<DiscoveredHost> { it.isProbableGateway }
            .thenBy { host ->
                host.ip.substringAfterLast(".").toIntOrNull() ?: 0
            }
    )
}

println("==================================================")
println("       RouterHub - Subnet & Gateway Scanner       ")
println("==================================================")

val localIp = detectLocalIp()
if (localIp != null) {
    println("Local IP Address: $localIp")
} else {
    println("Warning: Could not automatically detect local IP.")
}

val defaultSubnet = localIp?.substringBeforeLast(".") ?: "192.168.1"
val subnet = if (args.isNotEmpty() && args[0].isNotBlank()) args[0].trim() else defaultSubnet
val gatewayIp = "$subnet.1"

println("Target Subnet:    $subnet.1 - $subnet.254")
println("Probing Ports:    80 (HTTP), 443 (HTTPS), 8080 (Web Alt)")
println("--------------------------------------------------")
println("Scanning subnet...")

val discoveredHosts = scanSubnet(subnet = subnet, gatewayIp = gatewayIp)

println("\n--------------------------------------------------")
println("Scan complete! Found ${discoveredHosts.size} active host(s):")
println("--------------------------------------------------")

if (discoveredHosts.isEmpty()) {
    println("No web administration interfaces found on $subnet.0/24.")
} else {
    discoveredHosts.forEach { host ->
        val tag = if (host.isProbableGateway) " [Default Gateway / Router]" else ""
        println("IP: ${host.ip.padEnd(16)} Ports: ${host.openPorts.joinToString(", ").padEnd(14)}$tag")
        if (!host.serverBanner.isNullOrBlank()) {
            println("   Banner: ${host.serverBanner}")
        }
        val urlList = host.openPorts.joinToString(" or ") { port ->
            val scheme = if (port == 443) "https" else "http"
            val portSuffix = if (port == 80 || port == 443) "" else ":$port"
            "$scheme://${host.ip}$portSuffix"
        }
        println("   Access URL: $urlList")
    }
}
println("==================================================")

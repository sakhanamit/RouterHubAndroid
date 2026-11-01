package com.routerhub.network

import android.content.Context
import android.net.ConnectivityManager
import com.routerhub.model.DeviceType
import com.routerhub.model.NetworkDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Locale
import java.util.regex.Pattern

object NetworkScanner {

    private val PORTS_TO_CHECK = intArrayOf(80, 443, 22, 445, 8080, 8008, 9100)

    fun detectGatewayAndIp(context: Context): Pair<String?, String?> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val linkProps = cm.getLinkProperties(cm.activeNetwork) ?: return Pair(null, null)

            val gateway = linkProps.routes
                .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway?.hostAddress

            val ownIp = linkProps.linkAddresses
                .map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress

            Pair(gateway, ownIp)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    /**
     * Reads /proc/net/arp to retrieve MAC addresses known to the kernel.
     */
    fun readArpTable(): Map<String, String> {
        val arpMap = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line: String? = reader.readLine() // skip header
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.trim().split("\\s+".toRegex())
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val flags = tokens[2]
                        val mac = tokens[3]
                        if (flags != "0x0" && mac != "00:00:00:00:00:00" && mac.length == 17) {
                            arpMap[ip] = mac.uppercase(Locale.ROOT)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // /proc/net/arp fallback
        }
        return arpMap
    }

    fun lookupVendor(mac: String?): String {
        if (mac.isNullOrBlank()) return "Unknown"
        val cleanMac = mac.replace(":", "").replace("-", "").uppercase(Locale.ROOT)
        if (cleanMac.length < 6) return "Unknown"

        val firstByte = cleanMac.substring(0, 2).toIntOrNull(16) ?: 0
        val isRandomized = (firstByte and 0x02) != 0

        val prefix6 = cleanMac.substring(0, 6)

        val knownVendor = when (prefix6) {
            // TP-Link
            "8EE178", "00F361", "90DE80", "50D4F7", "C006C3", "E848B8", "B04E26", "AC84C6",
            "D807B6", "14EBB6", "30B5C2", "001D0F", "002127", "0023CD", "002586", "002719",
            "10FEED", "18A6F7", "1C3BF3", "3C46D8", "54E6FC", "60E327", "645601", "704F57",
            "74DA38", "7844FD", "8416F9", "98DE8A", "A0F3C1", "C46E1F", "CC3429", "E4D332",
            "EC086B", "F4F26D", "B4B024", "5CE91E", "28285D", "6C5AB0" -> "TP-Link"

            // OnePlus, Oppo, Realme
            "948701", "98ED85", "9CFCE8", "A4EB34", "AC3E6B", "C4AD34", "CC785C", "DC85DE",
            "E4B805", "F83880", "F86B12", "0C41E9", "20C9D0", "2C598A", "482CBD", "4C79BA",
            "608334", "6CD146", "70DF2F", "745A00", "74667F", "788CF8", "84C7EA", "8C7B9D",
            "B83765", "BCA58B" -> "OnePlus / Oppo / Realme"

            // Vivo / iQOO
            "00755C", "040E3C", "20AB37", "3438B7", "38563D", "543A7C", "64136C", "78923C",
            "88797E", "904CE5", "C444CD", "D86375", "E45A2E", "E8E8B7", "F4CB52" -> "Vivo / iQOO"

            // Xiaomi, Redmi, Poco
            "009EC8", "04CF8C", "0C1DAF", "14F65A", "185936", "2082C0", "286C07", "34800D",
            "50642B", "7811DC", "18C04E", "24E6BA", "2C18AE", "38A4ED", "48875A", "584498",
            "64CC22", "687258", "7451BA", "84F3EB", "88C397", "8CBEBE", "A47733", "B0E235",
            "BCF2AF", "C02AC4", "D4619D", "DC6DCD", "E4AAEC", "F01898", "F4F5DB" -> "Xiaomi / Redmi"

            // Samsung
            "0007AB", "001247", "001599", "001632", "0018AF", "002454", "002637", "34145F",
            "5001D9", "5492BE", "60AF6D", "8425DB", "88329B", "90187C", "946372", "980D2E",
            "A0821F", "AC5F3E", "B479A7", "C0B658", "CC07AB", "D0176A", "DC7144", "E47C01",
            "F47B5E" -> "Samsung"

            // Apple
            "000393", "000502", "0017F2", "0019E3", "001B63", "001CB3", "001D4F", "F0766F",
            "F099BF", "F40F24", "F41BA1", "F431C3", "F434F0", "F437B7", "1040F3", "109ADD",
            "1499E2", "183451", "1C5CF2", "20EE28", "280B5C", "34AB37", "38F9D3", "406C8F",
            "48D705", "50BC96", "54E43A", "600308", "647033", "70DEE2", "787B8A", "80B03D",
            "8866A5", "9027E4", "98B8E3", "A03BFB", "A4C361", "ACBC32", "B418D1", "BC5436",
            "C0847D", "C869CD", "D023DB", "DC2B61", "E06678", "E8802E", "F02765" -> "Apple"

            // Huawei / Honor
            "001861", "001E10", "002568", "18B430", "34CE00", "44650D", "50F5DA", "546009",
            "702C1F", "A4C494", "C8377B", "D07AEC", "E0191D", "E468A3", "F4C479", "786A89",
            "80B686", "885395", "9C28BF", "A08CF8", "B43052" -> "Huawei / Honor"

            // Espressif (IoT Smart Home / Plugs / Bulbs)
            "240AC4", "30AEA4", "A4CF12", "B4E62D", "BCDDC2", "D8A01D", "DC4F22", "E868E7",
            "246F28", "24B2DE", "308398", "3C71BF", "40F520", "4827E2", "4C7525", "5443B2",
            "5C497D", "68C63A", "70039F", "7C87CE", "840D8E", "84CC88", "943CC6", "94B97E",
            "94E686", "A0764E", "A47B9D", "B0A732", "C4DD57", "CC50E3", "D4D4DA", "E09806",
            "EC6260", "EC94CB" -> "Espressif (Smart Home/IoT)"

            // Tuya Smart (Smart Plugs, Relays, Bulbs)
            "10D561", "1869D8", "383262", "708976", "7CF666", "84E342", "A09208", "C03E48",
            "D4A651", "68572D" -> "Tuya (Smart IoT)"

            // Google / Nest
            "3C5AB4", "94103E", "D86C63", "3C8D20", "001A11", "48D6D5", "641666",
            "E4F042", "F88FCA" -> "Google / Nest"

            // Amazon Echo / Fire TV
            "FCA183", "747548", "6837E9", "50DCE7", "40B4CD", "00FC8B", "38F73D",
            "68545A", "8871E5", "AC63BE", "CC40D0" -> "Amazon Echo / FireTV"

            // Sony / PlayStation
            "30F9ED", "00041F", "0013A9", "0015C1", "0019C5", "001DBA", "0024BE", "080046",
            "709E29", "7C1E52", "AC9B0A", "F8461C" -> "Sony / PlayStation"

            // Microsoft / Xbox
            "00059A", "0012F0", "00155D", "001DD8", "7085C2", "DCB4C4", "281878", "485073",
            "5882A8", "985FD3", "C83F26" -> "Microsoft / Xbox"

            // LG TV
            "001F6B", "0026E2", "10F1F2", "203DB0", "344DFA", "40B034", "58A2B5", "64995D",
            "70288B", "88366C", "98D6F7", "A823FE", "B4E62A", "CC2D8C", "E85B5B" -> "LG Smart TV"

            // HP / Printers
            "0001E6", "000400", "000802", "000BDB", "000E7F", "001083", "00110A", "001279",
            "001438", "001708", "0018FE", "001A4B", "001CC4", "001E0B", "00215A", "002264",
            "00237D", "002481", "0025B3", "10604B", "1458D0", "18A958", "3C4A92", "705A0F",
            "9C8E99", "A45D36" -> "HP Printer / Device"

            // Dell
            "00065B", "000874", "001143", "00123F", "001372", "001422", "0015C5", "0016F0",
            "00188B", "0019B9", "001A6B", "001C23", "001D09", "001E4F", "002170", "002219",
            "0023AE", "0024E8", "002564", "14FE72", "180373", "1866DA", "24B6FD", "3417EB",
            "44A842", "5CF9DD", "74867A", "847BEB", "989096", "B8AC6F", "BC305B", "D4BE09",
            "E454E8", "F01FAF", "F8BC12" -> "Dell PC / Laptop"

            // Lenovo
            "0012FE", "0021CC", "002618", "081196", "28D244", "305A3A", "482C6A", "54EE75",
            "60D9A0", "70723C", "80000B", "902B34", "A41194", "B888E3", "C85B76", "E09467",
            "EC8EB5" -> "Lenovo PC / Laptop"

            // Intel
            "001788", "0024D7", "BCDE77", "40167E", "4851B7", "680571", "8086F2", "A0C589",
            "A434D9", "B49691", "E4029B", "EC63D7" -> "Intel PC / Laptop"

            // Netgear
            "0014D1", "00226B", "0026F2", "204E7F", "2C3033", "E0469A", "C40415", "000FB5",
            "04A151", "100C6B", "288088", "4494FC", "6C3B6B", "841B5E", "9C3DCF", "A00460",
            "B03956", "C0FFD4", "E4F4C6" -> "Netgear"

            // D-Link
            "002688", "001E58", "E0D55E", "B0C554", "7062B8", "00055D", "000D88", "001195",
            "001346", "0015E9", "00179A", "00195B", "001B11", "001CF0", "0022B0", "14D64D",
            "1C7EE5", "28107B", "340804", "78542E", "9094E4", "A0AB1B", "B8A386", "C0A0BB",
            "CCB255" -> "D-Link"

            // ASUS
            "000C43", "04D4C4", "107B44", "14DDA9", "2C4D54", "38D547", "0015F2", "0018F3",
            "001A92", "001BFC", "001D60", "001E8C", "002215", "002354", "00248C", "08606E",
            "086266", "10BF48", "1831BF", "20CF30", "3085A9", "50465D", "54A050", "60A44C",
            "704D7B", "74D02B", "88D7F6", "90E6BA", "AC220B", "BC107B", "D850E6", "E03F49" -> "ASUS"

            // Tenda & Mercusys
            "00B00C", "0495E6", "502B73", "C83A35", "E865D4" -> "Tenda Router"
            "3460F9", "7405A5", "D46E0E" -> "Mercusys Router"

            // Raspberry Pi
            "B827EB", "DCA632", "E45F01", "28CDC1", "D83ADD" -> "Raspberry Pi"

            // Roku
            "000D4B", "20F543", "84EA99", "B0A737", "DC3A5E" -> "Roku Streaming Device"

            else -> null
        }

        return when {
            knownVendor != null -> knownVendor
            isRandomized -> "Smartphone / PC (Private MAC)"
            else -> "Network Client"
        }
    }

    suspend fun scanHost(
        ip: String,
        gatewayIp: String?,
        ownIp: String?,
        cachedMacs: Map<String, String>
    ): NetworkDevice? = withContext(Dispatchers.IO) {
        val isGateway = (ip == gatewayIp)
        val isThisDevice = (ip == ownIp)

        val openPorts = mutableListOf<Int>()
        var minLatency: Long? = null

        // 1. Probe TCP ports with fast timeout
        for (port in PORTS_TO_CHECK) {
            val start = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 220)
                    val elapsed = System.currentTimeMillis() - start
                    openPorts.add(port)
                    if (minLatency == null || elapsed < minLatency!!) {
                        minLatency = elapsed
                    }
                }
            } catch (_: Exception) {
                // Port closed
            }
        }

        // 2. If no ports open, check ICMP reachability
        var isAlive = openPorts.isNotEmpty() || isGateway || isThisDevice
        if (!isAlive) {
            try {
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(ip).isReachable(250)
                if (reachable) {
                    isAlive = true
                    minLatency = System.currentTimeMillis() - start
                }
            } catch (_: Exception) {
                // Not reachable
            }
        }

        // 3. Check ARP table if the host was recorded during probe
        val mac = cachedMacs[ip] ?: readArpForIp(ip)
        if (mac != null && mac != "00:00:00:00:00:00") {
            isAlive = true
        }

        if (!isAlive) return@withContext null

        // 4. Resolve Canonical Hostname
        val hostname = try {
            val resolved = InetAddress.getByName(ip).canonicalHostName
            if (resolved != ip && resolved.isNotBlank()) resolved else null
        } catch (_: Exception) {
            null
        }

        // 5. Check HTTP HTML Title and Server Headers (Great for Routers, Printers, Cameras)
        val httpTitle = if (openPorts.contains(80) || openPorts.contains(443) || openPorts.contains(8080)) {
            val port = if (openPorts.contains(80)) 80 else if (openPorts.contains(443)) 443 else 8080
            fetchTitleAndBanner(ip, port)
        } else null

        // 6. Vendor & Device Classification
        val vendor = lookupVendor(mac)
        val deviceType = classifyDevice(
            isGateway = isGateway,
            isThisDevice = isThisDevice,
            openPorts = openPorts,
            vendor = vendor,
            httpTitle = httpTitle,
            hostname = hostname
        )

        NetworkDevice(
            ip = ip,
            mac = mac,
            hostname = hostname,
            vendor = vendor,
            deviceType = deviceType,
            isGateway = isGateway,
            isThisDevice = isThisDevice,
            openPorts = openPorts,
            pingMs = minLatency,
            httpTitle = httpTitle
        )
    }

    private fun readArpForIp(targetIp: String): String? {
        return try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // header
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.trim().split("\\s+".toRegex())
                    if (tokens.size >= 4 && tokens[0] == targetIp) {
                        val flags = tokens[2]
                        val mac = tokens[3]
                        if (flags != "0x0" && mac != "00:00:00:00:00:00") {
                            return mac.uppercase(Locale.ROOT)
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchTitleAndBanner(ip: String, port: Int): String? {
        val scheme = if (port == 443) "https" else "http"
        val portSuffix = if (port == 80 || port == 443) "" else ":$port"
        return try {
            val url = URI("$scheme://$ip$portSuffix/").toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 400
                readTimeout = 400
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            val server = conn.getHeaderField("Server")
            val auth = conn.getHeaderField("WWW-Authenticate")

            // Read the initial HTML response body to find <title>
            var bodyTitle: String? = null
            try {
                conn.inputStream.use { input ->
                    val reader = BufferedReader(InputStreamReader(input))
                    val buffer = CharArray(2048)
                    val read = reader.read(buffer)
                    if (read > 0) {
                        val snippet = String(buffer, 0, read)
                        val matcher = Pattern.compile("(?i)<title[^>]*>(.*?)</title>").matcher(snippet)
                        if (matcher.find()) {
                            val raw = matcher.group(1)?.trim()
                            if (!raw.isNullOrBlank() && raw.length < 50) {
                                bodyTitle = raw
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            conn.disconnect()

            when {
                !bodyTitle.isNullOrBlank() -> bodyTitle
                !server.isNullOrBlank() -> server.trim()
                !auth.isNullOrBlank() -> auth.trim()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun classifyDevice(
        isGateway: Boolean,
        isThisDevice: Boolean,
        openPorts: List<Int>,
        vendor: String,
        httpTitle: String?,
        hostname: String?
    ): DeviceType {
        if (isGateway) return DeviceType.ROUTER
        if (isThisDevice) return DeviceType.PHONE

        val lowerHost = (hostname ?: "").lowercase(Locale.ROOT)
        val lowerTitle = (httpTitle ?: "").lowercase(Locale.ROOT)

        if (lowerTitle.contains("router") || lowerTitle.contains("luci") || lowerTitle.contains("tp-link") ||
            lowerTitle.contains("d-link") || lowerTitle.contains("netgear") || lowerTitle.contains("asus") ||
            lowerTitle.contains("tenda") || lowerTitle.contains("mercusys") || lowerTitle.contains("wireless")
        ) {
            return DeviceType.ROUTER
        }

        if (openPorts.contains(9100) || lowerHost.contains("printer") || vendor.contains("Printer")) return DeviceType.PRINTER
        if (openPorts.contains(8008) || lowerHost.contains("chromecast") || lowerHost.contains("tv") || lowerHost.contains("roku") || vendor.contains("TV")) {
            return DeviceType.TV
        }
        if (openPorts.contains(445) || lowerHost.contains("desktop") || lowerHost.contains("laptop") || lowerHost.contains("pc") || vendor.contains("PC") || vendor.contains("Dell") || vendor.contains("Lenovo")) {
            return DeviceType.COMPUTER
        }
        if (vendor.contains("Espressif") || vendor.contains("Tuya") || vendor.contains("Smart") || lowerHost.contains("esp") || lowerHost.contains("iot")) {
            return DeviceType.IOT
        }
        if (vendor.contains("Apple") || vendor.contains("Samsung") || vendor.contains("Huawei") || vendor.contains("Xiaomi") ||
            vendor.contains("OnePlus") || vendor.contains("Oppo") || vendor.contains("Realme") || vendor.contains("Vivo") ||
            vendor.contains("Honor") || vendor.contains("Private MAC") || lowerHost.contains("phone") || lowerHost.contains("galaxy") || lowerHost.contains("iphone")
        ) {
            return DeviceType.PHONE
        }
        if (vendor.contains("TP-Link") || vendor.contains("Netgear") || vendor.contains("D-Link") || vendor.contains("ASUS") || vendor.contains("Tenda")) {
            return DeviceType.ROUTER
        }

        return DeviceType.UNKNOWN
    }

    suspend fun scanSubnet(
        subnetPrefix: String,
        gatewayIp: String?,
        ownIp: String?,
        onProgress: (scanned: Int, total: Int, foundDevice: NetworkDevice?) -> Unit
    ): List<NetworkDevice> = withContext(Dispatchers.IO) {
        val totalHosts = 254
        var scannedCount = 0
        val discovered = mutableListOf<NetworkDevice>()

        // 1. Snapshot initial ARP cache
        val initialArp = readArpTable()

        // 2. Scan in concurrent batches of 40
        (1..totalHosts).chunked(40).forEach { batch ->
            val jobs = batch.map { hostNum ->
                async {
                    val ip = "$subnetPrefix.$hostNum"
                    val device = scanHost(ip, gatewayIp, ownIp, initialArp)
                    Pair(ip, device)
                }
            }
            val results = jobs.awaitAll()
            // After pinging, re-read ARP table for newly recorded hardware entries
            val updatedArp = readArpTable()

            for ((ip, device) in results) {
                scannedCount++
                val finalDevice = if (device != null) {
                    if (device.mac == null && updatedArp.containsKey(ip)) {
                        val mac = updatedArp[ip]
                        val vendor = lookupVendor(mac)
                        device.copy(mac = mac, vendor = vendor)
                    } else device
                } else null

                if (finalDevice != null) {
                    discovered.add(finalDevice)
                }
                onProgress(scannedCount, totalHosts, finalDevice)
            }
        }

        // Sort: Gateway first, then This Device, then routers, then by IP
        discovered.sortedWith(
            compareByDescending<NetworkDevice> { it.isGateway }
                .thenByDescending { it.isThisDevice }
                .thenByDescending { it.deviceType == DeviceType.ROUTER }
                .thenBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
        )
    }
}

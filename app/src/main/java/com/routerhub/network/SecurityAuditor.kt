package com.routerhub.network

import android.content.Context
import android.net.ConnectivityManager
import com.routerhub.model.NetworkDevice
import com.routerhub.model.RouterDevice
import com.routerhub.model.SecurityAuditReport
import com.routerhub.model.SecurityCheckResult
import com.routerhub.model.VulnerabilitySeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

object SecurityAuditor {

    suspend fun auditNetworkSecurity(
        context: Context,
        gatewayIp: String?,
        savedRouters: List<RouterDevice>,
        discoveredDevices: List<NetworkDevice>,
        onProgress: (Float, String) -> Unit
    ): SecurityAuditReport = withContext(Dispatchers.IO) {
        val checks = mutableListOf<SecurityCheckResult>()
        var passedCount = 0
        var totalChecks = 0

        val targetGateway = gatewayIp ?: "192.168.0.1"

        // 1. Check Router Cleartext Admin Access (HTTP vs HTTPS)
        onProgress(0.15f, "Auditing Router Web Management protocol...")
        val httpOpen = isPortOpen(targetGateway, 80, 400)
        val httpsOpen = isPortOpen(targetGateway, 443, 400)
        totalChecks++

        if (httpOpen && !httpsOpen) {
            checks.add(
                SecurityCheckResult(
                    id = "VULN_HTTP_CLEARTEXT",
                    title = "Cleartext HTTP Admin Portal",
                    description = "Router web console at $targetGateway only accepts unencrypted HTTP. Login passwords and session tokens can be intercepted by anyone on the local Wi-Fi.",
                    severity = VulnerabilitySeverity.HIGH,
                    isPassed = false,
                    target = "$targetGateway:80",
                    recommendation = "Enable HTTPS management in your router's Advanced > Security settings to encrypt all administrative sessions."
                )
            )
        } else if (httpsOpen) {
            passedCount++
            checks.add(
                SecurityCheckResult(
                    id = "CHECK_HTTPS_ENABLED",
                    title = "Secure HTTPS Management Available",
                    description = "Router supports encrypted HTTPS administration on port 443.",
                    severity = VulnerabilitySeverity.INFO,
                    isPassed = true,
                    target = "$targetGateway:443",
                    recommendation = "Always access your router via https://$targetGateway to prevent eavesdropping."
                )
            )
        } else {
            passedCount++
            checks.add(
                SecurityCheckResult(
                    id = "CHECK_HTTP_STEALTH",
                    title = "Standard Web Ports Closed / Filtered",
                    description = "Ports 80 and 443 are not exposed on default addresses, or use custom administration ports.",
                    severity = VulnerabilitySeverity.INFO,
                    isPassed = true,
                    target = targetGateway,
                    recommendation = "Maintain custom management port configuration."
                )
            )
        }

        // 2. Check Insecure Legacy Telnet (Port 23)
        onProgress(0.30f, "Checking for insecure Telnet (Port 23)...")
        totalChecks++
        val telnetOpen = isPortOpen(targetGateway, 23, 350)
        if (telnetOpen) {
            checks.add(
                SecurityCheckResult(
                    id = "VULN_TELNET_EXPOSED",
                    title = "High Risk: Insecure Telnet Port 23 Open",
                    description = "Telnet remote terminal service is actively listening on $targetGateway. Telnet does not encrypt keystrokes, transmitting root passwords and system credentials in plaintext across the network.",
                    severity = VulnerabilitySeverity.CRITICAL,
                    isPassed = false,
                    target = "$targetGateway:23",
                    recommendation = "Immediately disable Telnet service in router firmware settings. Use SSH (Port 22) for remote command access."
                )
            )
        } else {
            passedCount++
            checks.add(
                SecurityCheckResult(
                    id = "CHECK_TELNET_CLOSED",
                    title = "Telnet Service Disabled",
                    description = "Insecure Telnet port 23 is closed, protecting terminal commands from plaintext interception.",
                    severity = VulnerabilitySeverity.INFO,
                    isPassed = true,
                    target = "$targetGateway:23",
                    recommendation = "Keep Telnet disabled."
                )
            )
        }

        // 3. Check Insecure Legacy FTP (Port 21)
        onProgress(0.45f, "Probing Legacy FTP (Port 21)...")
        totalChecks++
        val ftpOpen = isPortOpen(targetGateway, 21, 350)
        if (ftpOpen) {
            checks.add(
                SecurityCheckResult(
                    id = "VULN_FTP_EXPOSED",
                    title = "Unencrypted FTP Port 21 Exposed",
                    description = "Router USB/NAS file sharing is exposing standard FTP without TLS encryption.",
                    severity = VulnerabilitySeverity.MEDIUM,
                    isPassed = false,
                    target = "$targetGateway:21",
                    recommendation = "Disable legacy FTP in USB Storage settings or enable FTPS / SFTP with strong credentials."
                )
            )
        } else {
            passedCount++
            checks.add(
                SecurityCheckResult(
                    id = "CHECK_FTP_SECURE",
                    title = "Legacy FTP Port Closed",
                    description = "Standard FTP port 21 is closed on the router gateway.",
                    severity = VulnerabilitySeverity.INFO,
                    isPassed = true,
                    target = "$targetGateway:21",
                    recommendation = "Ensure network storage uses authenticated and encrypted protocols."
                )
            )
        }

        // 4. Check UPnP Risk (Port 5000 / 1900)
        onProgress(0.60f, "Auditing UPnP Vulnerability Risk...")
        totalChecks++
        val upnpOpen = isPortOpen(targetGateway, 5000, 350)
        if (upnpOpen) {
            checks.add(
                SecurityCheckResult(
                    id = "VULN_UPNP_ACTIVE",
                    title = "UPnP Service Detected (Potential Port Forwarding Risk)",
                    description = "Universal Plug and Play (UPnP) is active on $targetGateway:5000. Malware or compromised IoT devices on the LAN can instruct the router to open external WAN firewall ports without admin approval.",
                    severity = VulnerabilitySeverity.MEDIUM,
                    isPassed = false,
                    target = "$targetGateway:5000",
                    recommendation = "Turn off UPnP in your router's Advanced > NAT Forwarding settings unless required by gaming consoles."
                )
            )
        } else {
            passedCount++
            checks.add(
                SecurityCheckResult(
                    id = "CHECK_UPNP_SHIELDED",
                    title = "UPnP Management Port Shielded",
                    description = "Port 5000 is filtered or UPnP web services are disabled.",
                    severity = VulnerabilitySeverity.INFO,
                    isPassed = true,
                    target = "$targetGateway:5000",
                    recommendation = "Keep UPnP disabled to prevent unauthorized WAN pinholes."
                )
            )
        }

        // 5. Check DNS Security & Resolvers
        onProgress(0.75f, "Auditing DNS configuration and resolver integrity...")
        totalChecks++
        val (dnsServers, privateDnsName) = getDnsInfo(context)
        val dnsDesc = dnsServers.joinToString(", ").ifEmpty { targetGateway }

        val hasPrivateDns = !privateDnsName.isNullOrBlank()
        val allPrivateOrRouter = dnsServers.isNotEmpty() && dnsServers.all { isPrivateIp(it) || it == targetGateway }
        val hasKnownSecure = dnsServers.isNotEmpty() && dnsServers.any { isKnownSecureDns(it) }

        passedCount++
        when {
            hasPrivateDns -> {
                checks.add(
                    SecurityCheckResult(
                        id = "CHECK_DNS_PRIVATE_DOT",
                        title = "Encrypted Private DNS Active (DoT)",
                        description = "Android Private DNS ($privateDnsName) is actively encrypting domain queries over TLS (Port 853), shielding requests from ISP and local network eavesdropping.",
                        severity = VulnerabilitySeverity.INFO,
                        isPassed = true,
                        target = privateDnsName,
                        recommendation = "Keep Private DNS active for enhanced privacy on public and home Wi-Fi networks."
                    )
                )
            }
            hasKnownSecure -> {
                checks.add(
                    SecurityCheckResult(
                        id = "CHECK_DNS_SECURE_RESOLVER",
                        title = "Secure High-Reputation DNS Verified",
                        description = "Active DNS resolvers ($dnsDesc) belong to recognized high-performance privacy providers (Cloudflare / Google / Quad9 / OpenDNS / AdGuard).",
                        severity = VulnerabilitySeverity.INFO,
                        isPassed = true,
                        target = dnsDesc,
                        recommendation = "Consider also enabling Private DNS in Android Settings to ensure queries use encrypted TLS (DoT)."
                    )
                )
            }
            allPrivateOrRouter -> {
                checks.add(
                    SecurityCheckResult(
                        id = "CHECK_DNS_GATEWAY_PROXY",
                        title = "Local Gateway DNS Verified",
                        description = "DNS queries are routed through local gateway proxy at $dnsDesc. Local router forwards or caches domain records.",
                        severity = VulnerabilitySeverity.INFO,
                        isPassed = true,
                        target = dnsDesc,
                        recommendation = "To prevent ISP logging upstream, configure Cloudflare (1.1.1.1) or Google (8.8.8.8) inside router WAN settings, or enable Private DNS on Android."
                    )
                )
            }
            else -> {
                checks.add(
                    SecurityCheckResult(
                        id = "CHECK_DNS_ISP_STANDARD",
                        title = "Standard ISP / Network DNS In Use",
                        description = "Active DNS resolver(s): $dnsDesc. Standard unencrypted DNS provided by your ISP or network gateway.",
                        severity = VulnerabilitySeverity.INFO,
                        isPassed = true,
                        target = dnsDesc,
                        recommendation = "Active DNS is functional. For encrypted DNS over TLS (DoT) and protection against ISP tracking, enable Private DNS (e.g. dns.google or one.one.one.one) in Android Settings > Network & internet > Private DNS."
                    )
                )
            }
        }

        // 6. Check Saved Router Credentials Strength
        onProgress(0.85f, "Auditing saved credentials strength...")
        totalChecks++
        val weakCredentialsFound = savedRouters.filter { router ->
            val p = router.password.lowercase(Locale.ROOT)
            val u = router.username.lowercase(Locale.ROOT)
            p.isBlank() || p == "admin" || p == "password" || p == "123456" || p == "root" || p == u
        }

        if (weakCredentialsFound.isNotEmpty()) {
            val names = weakCredentialsFound.joinToString { it.name }
            checks.add(
                SecurityCheckResult(
                    id = "VULN_WEAK_PASSWORD",
                    title = "Default / Weak Router Password Detected",
                    description = "Saved credentials for [$names] appear to use default or weak passwords. Factory default credentials allow attackers or rogue scripts to easily take full control of the router.",
                    severity = VulnerabilitySeverity.HIGH,
                    isPassed = false,
                    target = names,
                    recommendation = "Change the admin password on your router web console to a strong passphrase (at least 12 characters with letters, numbers, and symbols)."
                )
            )
        } else {
            passedCount++
            checks.add(
                SecurityCheckResult(
                    id = "CHECK_PASSWORD_STRENGTH",
                    title = "Router Admin Passwords Secured",
                    description = "Saved router profiles do not use obvious factory defaults (e.g. admin/admin or blank passwords).",
                    severity = VulnerabilitySeverity.INFO,
                    isPassed = true,
                    recommendation = "Periodically update router passwords and rotate Wi-Fi WPA2/WPA3 keys."
                )
            )
        }

        // 7. Audit Subnet Devices (Unrecognized devices / SMB on LAN)
        onProgress(0.95f, "Auditing network device exposure...")
        totalChecks++
        val smbDevices = discoveredDevices.filter { it.openPorts.contains(445) }
        if (smbDevices.isNotEmpty()) {
            val list = smbDevices.joinToString { "${it.ip} (${it.vendor ?: "Unknown"})" }
            checks.add(
                SecurityCheckResult(
                    id = "VULN_SMB_DETECTED",
                    title = "File Sharing (SMB Port 445) Exposed on LAN",
                    description = "Devices with open SMB port 445: $list. SMB file sharing should be restricted to trusted machines to prevent lateral malware movement.",
                    severity = VulnerabilitySeverity.LOW,
                    isPassed = false,
                    target = smbDevices.first().ip,
                    recommendation = "Ensure SMBv1 is disabled and passwords are required for all network shares."
                )
            )
        } else {
            passedCount++
            checks.add(
                SecurityCheckResult(
                    id = "CHECK_SMB_CLEAN",
                    title = "No Insecure SMB Shares Exposed",
                    description = "No network devices are exposing open SMB port 445 without firewall rules.",
                    severity = VulnerabilitySeverity.INFO,
                    isPassed = true,
                    recommendation = "Maintain firewall isolation between guest devices and file storage."
                )
            )
        }

        onProgress(1.0f, "Audit complete!")

        // Calculate Security Score
        val score = ((passedCount.toDouble() / totalChecks.toDouble()) * 100).toInt().coerceIn(0, 100)

        val summary = when {
            score >= 90 -> "Excellent Security: Router and local network show strong defenses."
            score >= 70 -> "Good with Recommendations: A few configuration improvements recommended."
            score >= 50 -> "Moderate Risk: Several potential attack surfaces or cleartext protocols detected."
            else -> "High Risk: Critical vulnerabilities found. Review recommendations immediately."
        }

        SecurityAuditReport(
            score = score,
            checks = checks,
            scannedAt = System.currentTimeMillis(),
            summary = summary
        )
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getDnsInfo(context: Context): Pair<List<String>, String?> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val linkProps = cm.getLinkProperties(cm.activeNetwork)
            val servers = linkProps?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
            val privateDns = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                linkProps?.privateDnsServerName
            } else null
            Pair(servers, privateDns)
        } catch (_: Exception) {
            Pair(emptyList(), null)
        }
    }

    private fun isKnownSecureDns(ip: String): Boolean {
        val clean = ip.substringBefore("%").lowercase(Locale.ROOT)
        val knownDns = setOf(
            // Cloudflare
            "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2", "1.1.1.3", "1.0.0.3",
            "2606:4700:4700::1111", "2606:4700:4700::1001",
            "2606:4700:4700::1112", "2606:4700:4700::1002",
            "2606:4700:4700::1113", "2606:4700:4700::1003",
            // Google
            "8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844",
            // Quad9
            "9.9.9.9", "149.112.112.112", "9.9.9.10", "149.112.112.10", "9.9.9.11", "149.112.112.11",
            "2620:fe::fe", "2620:fe::9", "2620:fe::10", "2620:fe::11",
            // OpenDNS (Cisco)
            "208.67.222.222", "208.67.220.220", "208.67.222.123", "208.67.220.123",
            "2620:119:35::35", "2620:119:53::53",
            // AdGuard
            "94.140.14.14", "94.140.15.15", "94.140.14.15", "94.140.15.16",
            "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
            // CleanBrowsing
            "185.228.168.9", "185.228.169.9", "185.228.168.168", "185.228.169.168",
            // Control D
            "76.76.2.0", "76.76.10.0", "76.76.2.2", "76.76.10.2",
            // Comodo
            "8.26.56.26", "8.20.247.20"
        )
        return knownDns.contains(clean)
    }

    private fun isPrivateIp(ip: String): Boolean {
        return try {
            val clean = ip.substringBefore("%")
            val addr = InetAddress.getByName(clean)
            addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isLoopbackAddress
        } catch (_: Exception) {
            false
        }
    }
}

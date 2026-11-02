package com.routerhub.network

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
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

    private val PORTS_TO_CHECK = intArrayOf(
        80, 443, 8080, 8443, 22, 23, 445, 139, 8008, 8009, 9100, 631, 554, 5000
    )

    fun getServiceName(port: Int): String = when (port) {
        80 -> "HTTP Web"
        443 -> "HTTPS SSL"
        8080 -> "HTTP Alt"
        8443 -> "HTTPS Alt"
        22 -> "SSH"
        23 -> "Telnet"
        445 -> "SMB"
        139 -> "NetBIOS"
        8008 -> "Google Cast"
        8009 -> "Cast SSL"
        9100 -> "Raw Print"
        631 -> "IPP Print"
        554 -> "RTSP Camera"
        5000 -> "UPnP / NAS"
        else -> "Port $port"
    }

    fun detectGatewayAndIp(context: Context): Pair<String?, String?> {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val dhcp = wm?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0 && dhcp.ipAddress != 0) {
                val gateway = formatIntToIp(dhcp.gateway)
                val ownIp = formatIntToIp(dhcp.ipAddress)
                if (gateway != "0.0.0.0" && ownIp != "0.0.0.0") {
                    return Pair(gateway, ownIp)
                }
            }
        } catch (_: Exception) {
            // Fallback to ConnectivityManager LinkProperties
        }

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

    private fun formatIntToIp(ipInt: Int): String {
        return "${ipInt and 0xFF}.${ipInt shr 8 and 0xFF}.${ipInt shr 16 and 0xFF}.${ipInt shr 24 and 0xFF}"
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

    fun lookupVendor(mac: String?, isGateway: Boolean = false): String {
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
            "0013E8", "0024D7", "BCDE77", "40167E", "4851B7", "680571", "8086F2", "A0C589",
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

            // Cisco & Linksys
            "000142", "000143", "0001C7", "0001C9", "000164", "000216", "000217", "00024A",
            "00024B", "0002FD", "0003FD", "00044D", "00049F", "000531", "000573", "00059A",
            "000628", "000652", "00070E", "000750", "00077D", "000784", "000785", "0007B3" -> "Cisco"
            "00045A", "000625", "000C41", "000E08", "000E35", "000F66", "001150", "001217",
            "001310", "0014BF", "0016B6", "001839", "0018F8", "001A70", "001C10", "001D7E" -> "Linksys"

            // Ubiquiti UniFi
            "00156D", "002722", "0418D6", "18E829", "24A43C", "44D9E7", "60E32B", "68D79A",
            "70A741", "7483C2", "788A20", "802AA8", "94F7AD", "AC8BA9", "B4FBE4", "DC9FDB" -> "Ubiquiti UniFi"

            // MikroTik
            "000C42", "2C56DC", "488F5A", "4C5E0C", "64D154", "744D28", "B869F4", "D4CA6D", "E48D8C" -> "MikroTik"

            // Printers (Canon, Brother, Epson)
            "000085", "001E8F", "180C1A", "70820E", "84BA3B", "A4EE57", "B40C25", "D8492F" -> "Canon Printer"
            "008077", "001BA9", "30055C", "40B076", "80691A" -> "Brother Printer"
            "000048", "0021B7", "0026AB", "28C68E", "44D244", "64EB8C", "AC1702", "B46BF9" -> "Epson Printer"

            // Smart Audio, Lighting, Storage
            "000E58", "08FBAE", "48A6B8", "5C026E", "5C648E", "7828CA", "949F3E", "B8E937" -> "Sonos Audio"
            "001788", "ECB5FA" -> "Philips Hue"
            "001132" -> "Synology NAS"
            "00089B", "245EBE" -> "QNAP NAS"

            // Raspberry Pi
            "B827EB", "DCA632", "E45F01", "28CDC1", "D83ADD" -> "Raspberry Pi"

            // Roku
            "000D4B", "20F543", "84EA99", "B0A737", "DC3A5E" -> "Roku Streaming Device"

            else -> null
        }

        return when {
            knownVendor != null -> knownVendor
            isGateway -> "Gateway Router"
            isRandomized -> "Smartphone / PC (Private MAC)"
            else -> "Network Client"
        }
    }

    /**
     * Extracts structured Brand and Model names from network parameters.
     */
    fun extractBrandAndModel(
        vendor: String?,
        mac: String?,
        hostname: String?,
        httpTitle: String?,
        openPorts: List<Int>,
        isGateway: Boolean,
        isThisDevice: Boolean
    ): Pair<String, String> {
        if (isThisDevice) {
            val b = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            val m = Build.MODEL
            return Pair(b, m)
        }

        val cleanVendor = if (isGateway && (vendor == null || vendor.contains("Private MAC") || vendor == "Unknown")) {
            "Gateway Router"
        } else {
            vendor ?: "Unknown"
        }
        val lowerHost = (hostname ?: "").lowercase(Locale.ROOT)
        val lowerTitle = (httpTitle ?: "").lowercase(Locale.ROOT)

        // 1. Determine Brand
        var detectedBrand = when {
            cleanVendor.contains("TP-Link", true) -> "TP-Link"
            cleanVendor.contains("ASUS", true) -> "ASUS"
            cleanVendor.contains("Netgear", true) -> "Netgear"
            cleanVendor.contains("D-Link", true) -> "D-Link"
            cleanVendor.contains("Cisco", true) -> "Cisco"
            cleanVendor.contains("Linksys", true) -> "Linksys"
            cleanVendor.contains("Ubiquiti", true) || cleanVendor.contains("UniFi", true) -> "Ubiquiti"
            cleanVendor.contains("MikroTik", true) -> "MikroTik"
            cleanVendor.contains("Tenda", true) -> "Tenda"
            cleanVendor.contains("Mercusys", true) -> "Mercusys"
            cleanVendor.contains("Apple", true) -> "Apple"
            cleanVendor.contains("Samsung", true) -> "Samsung"
            cleanVendor.contains("Xiaomi", true) || cleanVendor.contains("Redmi", true) -> "Xiaomi"
            cleanVendor.contains("OnePlus", true) -> "OnePlus"
            cleanVendor.contains("Oppo", true) -> "Oppo"
            cleanVendor.contains("Realme", true) -> "Realme"
            cleanVendor.contains("Vivo", true) || cleanVendor.contains("iQOO", true) -> "Vivo"
            cleanVendor.contains("Huawei", true) || cleanVendor.contains("Honor", true) -> "Huawei"
            cleanVendor.contains("Google", true) || cleanVendor.contains("Nest", true) -> "Google"
            cleanVendor.contains("Amazon", true) -> "Amazon"
            cleanVendor.contains("Sony", true) || cleanVendor.contains("PlayStation", true) -> "Sony"
            cleanVendor.contains("Microsoft", true) || cleanVendor.contains("Xbox", true) -> "Microsoft"
            cleanVendor.contains("HP", true) -> "HP"
            cleanVendor.contains("Canon", true) -> "Canon"
            cleanVendor.contains("Epson", true) -> "Epson"
            cleanVendor.contains("Brother", true) -> "Brother"
            cleanVendor.contains("Dell", true) -> "Dell"
            cleanVendor.contains("Lenovo", true) -> "Lenovo"
            cleanVendor.contains("Intel", true) -> "Intel"
            cleanVendor.contains("Espressif", true) -> "Espressif"
            cleanVendor.contains("Tuya", true) -> "Tuya"
            cleanVendor.contains("Philips", true) -> "Philips"
            cleanVendor.contains("Sonos", true) -> "Sonos"
            cleanVendor.contains("LG", true) -> "LG"
            cleanVendor.contains("Synology", true) -> "Synology"
            cleanVendor.contains("QNAP", true) -> "QNAP"
            cleanVendor.contains("Raspberry", true) -> "Raspberry Pi"
            cleanVendor.contains("Roku", true) -> "Roku"
            else -> null
        }

        if (detectedBrand == null) {
            detectedBrand = when {
                lowerTitle.contains("tp-link") || lowerHost.contains("tplink") -> "TP-Link"
                lowerTitle.contains("asus") || lowerHost.contains("asus") -> "ASUS"
                lowerTitle.contains("netgear") || lowerHost.contains("netgear") -> "Netgear"
                lowerTitle.contains("d-link") || lowerHost.contains("dlink") -> "D-Link"
                lowerTitle.contains("openwrt") || lowerTitle.contains("luci") -> "OpenWrt"
                lowerTitle.contains("ubiquiti") || lowerTitle.contains("unifi") -> "Ubiquiti"
                lowerTitle.contains("mikrotik") || lowerHost.contains("mikrotik") -> "MikroTik"
                lowerHost.contains("iphone") || lowerHost.contains("ipad") || lowerHost.contains("macbook") || lowerHost.contains("apple") -> "Apple"
                lowerHost.contains("galaxy") || lowerHost.contains("samsung") -> "Samsung"
                lowerHost.contains("pixel") -> "Google"
                isGateway -> "Gateway Router"
                else -> if (cleanVendor != "Unknown" && !cleanVendor.contains("Private MAC")) cleanVendor else "Network Device"
            }
        }

        // 2. Determine Model
        var detectedModel: String? = null

        // Try extracting model from HTTP title (often holds exact model like Archer C6, RT-AC68U)
        if (!httpTitle.isNullOrBlank()) {
            val t = httpTitle.trim()
            val cleaned = t.replace(Regex("(?i)^(tp-link|asus|netgear|d-link|tenda|mercusys|wireless|router|home|admin|login|portal)\\s*"), "")
                .replace(Regex("(?i)\\s*(router|management|console|web\\s*interface|portal|login|administration)$"), "")
                .trim()
            if (cleaned.length in 2..40) {
                detectedModel = cleaned
            } else if (t.length <= 40) {
                detectedModel = t
            }
        }

        // Try extracting model from canonical hostname
        if (detectedModel.isNullOrBlank() && !hostname.isNullOrBlank()) {
            val h = hostname.trim()
            val friendly = h.replace(".local", "").replace(".lan", "").replace(".home", "")
                .replace("-", " ").replace("_", " ")
            if (friendly.length in 3..35 && !friendly.all { it.isDigit() || it == '.' }) {
                detectedModel = friendly.split(" ").filter { it.isNotBlank() }
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            }
        }

        // Fallback to role-based model name
        if (detectedModel.isNullOrBlank()) {
            detectedModel = when {
                isGateway -> "Primary Gateway"
                openPorts.contains(9100) || openPorts.contains(631) -> "$detectedBrand Network Printer"
                openPorts.contains(554) -> "$detectedBrand IP Camera"
                openPorts.contains(8008) || openPorts.contains(8009) -> "$detectedBrand Smart TV / Streamer"
                openPorts.contains(5000) -> "$detectedBrand NAS Storage"
                openPorts.contains(445) || openPorts.contains(139) -> "$detectedBrand Workstation"
                detectedBrand == "Espressif" -> "ESP32 / ESP8266 IoT Chip"
                detectedBrand == "Tuya" -> "Smart Life IoT Device"
                detectedBrand == "Philips" -> "Hue Smart Lighting Bridge"
                detectedBrand == "Sonos" -> "Wireless Audio Speaker"
                detectedBrand == "Roku" -> "Roku Streaming Device"
                detectedBrand == "Apple" -> "Apple Device"
                detectedBrand == "Samsung" -> "Samsung Device"
                openPorts.contains(80) || openPorts.contains(443) || openPorts.contains(8080) -> "$detectedBrand Web Console"
                else -> "$detectedBrand Device"
            }
        }

        return Pair(detectedBrand, detectedModel)
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
        val vendor = lookupVendor(mac, isGateway)
        val deviceType = classifyDevice(
            isGateway = isGateway,
            isThisDevice = isThisDevice,
            openPorts = openPorts,
            vendor = vendor,
            httpTitle = httpTitle,
            hostname = hostname
        )

        val services = openPorts.map { getServiceName(it) }
        val (brand, model) = extractBrandAndModel(
            vendor = vendor,
            mac = mac,
            hostname = hostname,
            httpTitle = httpTitle,
            openPorts = openPorts,
            isGateway = isGateway,
            isThisDevice = isThisDevice
        )

        NetworkDevice(
            ip = ip,
            mac = mac,
            hostname = hostname,
            vendor = vendor,
            brand = brand,
            model = model,
            deviceType = deviceType,
            isGateway = isGateway,
            isThisDevice = isThisDevice,
            openPorts = openPorts,
            services = services,
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
        val scheme = if (port == 443 || port == 8443) "https" else "http"
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

    fun classifyDevice(
        isGateway: Boolean = false,
        isThisDevice: Boolean = false,
        openPorts: List<Int> = emptyList(),
        vendor: String = "Unknown",
        httpTitle: String? = null,
        hostname: String? = null
    ): DeviceType {
        if (isGateway) return DeviceType.ROUTER
        if (isThisDevice) return DeviceType.PHONE

        val lowerHost = (hostname ?: "").lowercase(Locale.ROOT)
        val lowerTitle = (httpTitle ?: "").lowercase(Locale.ROOT)

        if (lowerTitle.contains("router") || lowerTitle.contains("luci") || lowerTitle.contains("tp-link") ||
            lowerTitle.contains("d-link") || lowerTitle.contains("netgear") || lowerTitle.contains("asus") ||
            lowerTitle.contains("tenda") || lowerTitle.contains("mercusys") || lowerTitle.contains("wireless") ||
            lowerTitle.contains("unifi") || lowerTitle.contains("mikrotik")
        ) {
            return DeviceType.ROUTER
        }

        if (openPorts.contains(554) || lowerHost.contains("camera") || lowerHost.contains("dvr") || lowerHost.contains("nvr") || lowerTitle.contains("camera")) {
            return DeviceType.CAMERA
        }
        if (openPorts.contains(5000) || vendor.contains("Synology") || vendor.contains("QNAP") || lowerHost.contains("nas") || lowerTitle.contains("nas") || lowerTitle.contains("diskstation")) {
            return DeviceType.STORAGE
        }
        if (openPorts.contains(9100) || openPorts.contains(631) || lowerHost.contains("printer") || vendor.contains("Printer") || vendor.contains("Canon") || vendor.contains("Epson") || vendor.contains("Brother")) {
            return DeviceType.PRINTER
        }
        if (openPorts.contains(8008) || openPorts.contains(8009) || lowerHost.contains("chromecast") || lowerHost.contains("tv") || lowerHost.contains("roku") || vendor.contains("TV") || vendor.contains("Sonos")) {
            return DeviceType.TV
        }
        if (openPorts.contains(445) || openPorts.contains(139) || lowerHost.contains("desktop") || lowerHost.contains("laptop") || lowerHost.contains("pc") || vendor.contains("PC") || vendor.contains("Dell") || vendor.contains("Lenovo") || vendor.contains("Intel")) {
            return DeviceType.COMPUTER
        }
        if (vendor.contains("Espressif") || vendor.contains("Tuya") || vendor.contains("Smart") || vendor.contains("Philips") || lowerHost.contains("esp") || lowerHost.contains("iot")) {
            return DeviceType.IOT
        }
        if (vendor.contains("Apple") || vendor.contains("Samsung") || vendor.contains("Huawei") || vendor.contains("Xiaomi") ||
            vendor.contains("OnePlus") || vendor.contains("Oppo") || vendor.contains("Realme") || vendor.contains("Vivo") ||
            vendor.contains("Honor") || vendor.contains("Private MAC") || lowerHost.contains("phone") || lowerHost.contains("galaxy") || lowerHost.contains("iphone")
        ) {
            return DeviceType.PHONE
        }
        if (vendor.contains("TP-Link") || vendor.contains("Netgear") || vendor.contains("D-Link") || vendor.contains("ASUS") || vendor.contains("Tenda") || vendor.contains("Cisco") || vendor.contains("Linksys") || vendor.contains("Ubiquiti") || vendor.contains("MikroTik")) {
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
                    val resolvedMac = if (device.mac == null && updatedArp.containsKey(ip)) updatedArp[ip] else device.mac
                    val resolvedVendor = if (resolvedMac != null) lookupVendor(resolvedMac) else device.vendor
                    val (brand, model) = extractBrandAndModel(
                        vendor = resolvedVendor,
                        mac = resolvedMac,
                        hostname = device.hostname,
                        httpTitle = device.httpTitle,
                        openPorts = device.openPorts,
                        isGateway = device.isGateway,
                        isThisDevice = device.isThisDevice
                    )
                    device.copy(
                        mac = resolvedMac,
                        vendor = resolvedVendor,
                        brand = brand,
                        model = model
                    )
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

    /**
     * Specifically scans and discovers all router candidates on the network.
     * Probes gateway IP, common router IPs, and local subnet hosts with web/router services.
     */
    suspend fun discoverRouters(
        subnetPrefix: String,
        gatewayIp: String?,
        ownIp: String?,
        onRouterDiscovered: (NetworkDevice) -> Unit
    ): List<NetworkDevice> = withContext(Dispatchers.IO) {
        val discoveredList = mutableListOf<NetworkDevice>()
        val seenIps = mutableSetOf<String>()
        val initialArp = readArpTable()

        fun checkAndAddRouter(device: NetworkDevice) {
            val isRouter = device.isGateway ||
                    device.deviceType == DeviceType.ROUTER ||
                    device.openPorts.contains(80) ||
                    device.openPorts.contains(443) ||
                    device.openPorts.contains(8080) ||
                    device.openPorts.contains(8443) ||
                    (device.vendor != null && (
                        device.vendor.contains("TP-Link", ignoreCase = true) ||
                        device.vendor.contains("Netgear", ignoreCase = true) ||
                        device.vendor.contains("ASUS", ignoreCase = true) ||
                        device.vendor.contains("D-Link", ignoreCase = true) ||
                        device.vendor.contains("Tenda", ignoreCase = true) ||
                        device.vendor.contains("Mercusys", ignoreCase = true) ||
                        device.vendor.contains("Cisco", ignoreCase = true) ||
                        device.vendor.contains("Linksys", ignoreCase = true) ||
                        device.vendor.contains("Ubiquiti", ignoreCase = true) ||
                        device.vendor.contains("MikroTik", ignoreCase = true) ||
                        device.vendor.contains("Router", ignoreCase = true)
                    )) ||
                    (device.httpTitle != null && (
                        device.httpTitle.contains("router", ignoreCase = true) ||
                        device.httpTitle.contains("admin", ignoreCase = true) ||
                        device.httpTitle.contains("luci", ignoreCase = true) ||
                        device.httpTitle.contains("gateway", ignoreCase = true) ||
                        device.httpTitle.contains("unifi", ignoreCase = true) ||
                        device.httpTitle.contains("wireless", ignoreCase = true)
                    ))

            if (isRouter && seenIps.add(device.ip)) {
                discoveredList.add(device)
                onRouterDiscovered(device)
            }
        }

        // Priority 1: Check default gateway immediately if available
        if (gatewayIp != null) {
            scanHost(gatewayIp, gatewayIp, ownIp, initialArp)?.let { checkAndAddRouter(it) }
        }

        // Priority 2: Probe most common router host IPs in parallel
        val priorityIps = listOfNotNull(
            gatewayIp,
            "$subnetPrefix.1",
            "$subnetPrefix.254",
            "$subnetPrefix.2",
            "$subnetPrefix.100",
            "192.168.0.1",
            "192.168.1.1",
            "192.168.2.1",
            "192.168.10.1",
            "192.168.0.254",
            "192.168.1.254",
            "10.0.0.1",
            "10.0.1.1"
        ).distinct().filter { !seenIps.contains(it) }

        val priorityJobs = priorityIps.map { ip ->
            async {
                scanHost(ip, gatewayIp, ownIp, initialArp)
            }
        }
        priorityJobs.awaitAll().filterNotNull().forEach { checkAndAddRouter(it) }

        // Priority 3: Full subnet sweep in concurrent batches for any secondary APs/routers
        (1..254).chunked(40).forEach { batch ->
            val jobs = batch.mapNotNull { hostNum ->
                val ip = "$subnetPrefix.$hostNum"
                if (!seenIps.contains(ip)) {
                    async { scanHost(ip, gatewayIp, ownIp, initialArp) }
                } else null
            }

            val results = jobs.awaitAll().filterNotNull()
            val updatedArp = readArpTable()
            for (res in results) {
                val resolvedMac = if (res.mac == null && updatedArp.containsKey(res.ip)) updatedArp[res.ip] else res.mac
                val resolvedVendor = if (resolvedMac != null) lookupVendor(resolvedMac) else res.vendor
                val (brand, model) = extractBrandAndModel(
                    vendor = resolvedVendor,
                    mac = resolvedMac,
                    hostname = res.hostname,
                    httpTitle = res.httpTitle,
                    openPorts = res.openPorts,
                    isGateway = res.isGateway,
                    isThisDevice = res.isThisDevice
                )
                val finalDevice = res.copy(
                    mac = resolvedMac,
                    vendor = resolvedVendor,
                    brand = brand,
                    model = model
                )
                checkAndAddRouter(finalDevice)
            }
        }

        discoveredList.sortedWith(
            compareByDescending<NetworkDevice> { it.isGateway }
                .thenByDescending { it.deviceType == DeviceType.ROUTER }
                .thenBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
        )
    }
}

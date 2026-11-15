package com.routerhub

import com.routerhub.model.DeviceType
import com.routerhub.model.NetworkDevice
import com.routerhub.model.RouterDevice
import com.routerhub.model.SecurityAuditReport
import com.routerhub.model.SecurityCheckResult
import com.routerhub.model.VulnerabilitySeverity
import com.routerhub.network.NetworkScanner
import com.routerhub.network.SpeedTestPhase
import com.routerhub.network.SpeedTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterHubUnitTest {

    @Test
    fun testVendorLookupTPLink() {
        val mac = "8E:E1:78:12:34:56"
        val vendor = NetworkScanner.lookupVendor(mac)
        assertEquals("TP-Link", vendor)
    }

    @Test
    fun testVendorLookupAsus() {
        val mac = "00:0C:43:AA:BB:CC"
        val vendor = NetworkScanner.lookupVendor(mac)
        assertEquals("ASUS", vendor)
    }

    @Test
    fun testVendorLookupApple() {
        val mac = "F0:76:6F:11:22:33"
        val vendor = NetworkScanner.lookupVendor(mac)
        assertEquals("Apple", vendor)
    }

    @Test
    fun testVendorLookupEspressif() {
        val mac = "24:0A:C4:00:11:22"
        val vendor = NetworkScanner.lookupVendor(mac)
        assertEquals("Espressif (Smart Home/IoT)", vendor)
    }

    @Test
    fun testVendorLookupEmptyOrInvalid() {
        assertEquals("Unknown", NetworkScanner.lookupVendor(null))
        assertEquals("Unknown", NetworkScanner.lookupVendor(""))
        assertEquals("Unknown", NetworkScanner.lookupVendor("12:34"))
    }

    @Test
    fun testRouterDeviceCreationAndDefaults() {
        val router = RouterDevice(
            id = "router_1",
            name = "Main Living Room",
            ip = "192.168.0.1"
        )
        assertEquals("router_1", router.id)
        assertEquals("Main Living Room", router.name)
        assertEquals("192.168.0.1", router.ip)
        assertEquals("admin", router.username)
        assertEquals("", router.password)
    }

    @Test
    fun testVendorLookupExtendedBrands() {
        assertEquals("Cisco", NetworkScanner.lookupVendor("00:01:42:11:22:33"))
        assertEquals("Canon Printer", NetworkScanner.lookupVendor("00:00:85:AA:BB:CC"))
        assertEquals("Synology NAS", NetworkScanner.lookupVendor("00:11:32:44:55:66"))
        assertEquals("Philips Hue", NetworkScanner.lookupVendor("00:17:88:77:88:99"))
        assertEquals("Ubiquiti UniFi", NetworkScanner.lookupVendor("00:15:6D:99:88:77"))
    }

    @Test
    fun testExtractBrandAndModel() {
        val (brand1, model1) = NetworkScanner.extractBrandAndModel(
            vendor = "TP-Link",
            mac = "8E:E1:78:00:11:22",
            hostname = "archer-c6.lan",
            httpTitle = "TP-Link Archer C6",
            openPorts = listOf(80, 443),
            isGateway = true,
            isThisDevice = false
        )
        assertEquals("TP-Link", brand1)
        assertEquals("Archer C6", model1)

        val (brand2, model2) = NetworkScanner.extractBrandAndModel(
            vendor = "Synology NAS",
            mac = "00:11:32:00:11:22",
            hostname = "nas-diskstation",
            httpTitle = "Synology DiskStation DS920+",
            openPorts = listOf(5000, 5001),
            isGateway = false,
            isThisDevice = false
        )
        assertEquals("Synology", brand2)
        assertTrue(model2.contains("DiskStation") || model2.contains("DS920+"))
    }

    @Test
    fun testGetServiceName() {
        assertEquals("HTTP Web", NetworkScanner.getServiceName(80))
        assertEquals("HTTPS SSL", NetworkScanner.getServiceName(443))
        assertEquals("RTSP Camera", NetworkScanner.getServiceName(554))
        assertEquals("SMB", NetworkScanner.getServiceName(445))
        assertEquals("Raw Print", NetworkScanner.getServiceName(9100))
        assertEquals("Port 12345", NetworkScanner.getServiceName(12345))
    }

    @Test
    fun testDeviceClassificationCameraAndStorage() {
        val camType = NetworkScanner.classifyDevice(
            openPorts = listOf(554, 80),
            httpTitle = "Hikvision IP Camera",
            hostname = "cam-front",
            vendor = "Hikvision",
            isGateway = false
        )
        assertEquals(DeviceType.CAMERA, camType)

        val nasType = NetworkScanner.classifyDevice(
            openPorts = listOf(5000, 445),
            httpTitle = "Synology DiskStation",
            hostname = "synology-nas",
            vendor = "Synology NAS",
            isGateway = false
        )
        assertEquals(DeviceType.STORAGE, nasType)
    }

    @Test
    fun testNetworkDeviceModel() {
        val device = NetworkDevice(
            ip = "192.168.0.1",
            mac = "8E:E1:78:12:34:56",
            hostname = "router.lan",
            vendor = "TP-Link",
            deviceType = DeviceType.ROUTER,
            isGateway = true,
            isThisDevice = false,
            openPorts = listOf(80, 443),
            pingMs = 2L,
            httpTitle = "TP-Link Archer C6"
        )
        assertTrue(device.isGateway)
        assertFalse(device.isThisDevice)
        assertEquals(DeviceType.ROUTER, device.deviceType)
        assertEquals(2, device.openPorts.size)
        assertEquals("TP-Link Archer C6", device.httpTitle)
    }

    @Test
    fun testSecurityAuditReport() {
        val checks = listOf(
            SecurityCheckResult(
                id = "CHECK_1",
                title = "HTTPS Admin",
                description = "Secure management active",
                severity = VulnerabilitySeverity.INFO,
                isPassed = true,
                recommendation = "Keep active"
            ),
            SecurityCheckResult(
                id = "CHECK_2",
                title = "Telnet Disabled",
                description = "Telnet is closed",
                severity = VulnerabilitySeverity.CRITICAL,
                isPassed = true,
                recommendation = "No action needed"
            )
        )
        val report = SecurityAuditReport(
            score = 100,
            checks = checks,
            summary = "Network security is optimal"
        )
        assertEquals(100, report.score)
        assertEquals(2, report.checks.size)
        assertTrue(report.checks.all { it.isPassed })
    }

    @Test
    fun testSpeedTestResultDefaultsAndTransitions() {
        val defaultResult = SpeedTestResult()
        assertEquals(SpeedTestPhase.IDLE, defaultResult.phase)
        assertEquals(0f, defaultResult.progress, 0.001f)
        assertEquals(0L, defaultResult.pingMs)
        assertEquals(0f, defaultResult.currentDownloadMbps, 0.001f)
        assertEquals(0f, defaultResult.peakDownloadMbps, 0.001f)

        val activeResult = defaultResult.copy(
            phase = SpeedTestPhase.DOWNLOAD,
            progress = 0.45f,
            currentDownloadMbps = 84.5f,
            peakDownloadMbps = 92.1f,
            avgDownloadMbps = 86.0f,
            pingMs = 15L,
            jitterMs = 2L,
            downloadBytes = 45_000_000L
        )
        assertEquals(SpeedTestPhase.DOWNLOAD, activeResult.phase)
        assertEquals(0.45f, activeResult.progress, 0.001f)
        assertEquals(84.5f, activeResult.currentDownloadMbps, 0.001f)
        assertEquals(92.1f, activeResult.peakDownloadMbps, 0.001f)
        assertEquals(86.0f, activeResult.avgDownloadMbps, 0.001f)
        assertEquals(15L, activeResult.pingMs)
        assertEquals(2L, activeResult.jitterMs)
        assertEquals(45_000_000L, activeResult.downloadBytes)
    }

    @Test
    fun testSpeedTestCompletedMetrics() {
        val history = listOf(20f, 45f, 65f, 85f, 95f, 98f)
        val completeResult = SpeedTestResult(
            phase = SpeedTestPhase.COMPLETE,
            progress = 1.0f,
            pingMs = 12L,
            jitterMs = 1L,
            currentDownloadMbps = 95f,
            peakDownloadMbps = 98f,
            avgDownloadMbps = 95f,
            currentUploadMbps = 42f,
            peakUploadMbps = 45f,
            avgUploadMbps = 42f,
            downloadBytes = 110_000_000L,
            uploadBytes = 35_000_000L,
            speedHistory = history,
            statusText = "Speed Test Completed Successfully"
        )

        assertEquals(SpeedTestPhase.COMPLETE, completeResult.phase)
        assertEquals(1.0f, completeResult.progress, 0.001f)
        assertTrue(completeResult.peakDownloadMbps >= completeResult.avgDownloadMbps)
        assertTrue(completeResult.peakUploadMbps >= completeResult.avgUploadMbps)
        assertEquals(6, completeResult.speedHistory.size)
        assertEquals(98f, completeResult.speedHistory.maxOrNull() ?: 0f, 0.001f)
    }

    @Test
    fun testFormatIntToIp() {
        // 192.168.0.1 in little-endian integer representation:
        // byte0: 192, byte1: 168, byte2: 0, byte3: 1
        val ipInt = 192 or (168 shl 8) or (0 shl 16) or (1 shl 24)
        val formatted = NetworkScanner.formatIntToIp(ipInt)
        assertEquals("192.168.0.1", formatted)

        val loopbackInt = 127 or (0 shl 8) or (0 shl 16) or (1 shl 24)
        assertEquals("127.0.0.1", NetworkScanner.formatIntToIp(loopbackInt))
    }

    @Test
    fun testDeviceClassificationComprehensive() {
        // Printer
        val printer = NetworkScanner.classifyDevice(
            openPorts = listOf(9100),
            vendor = "HP Printer / Device"
        )
        assertEquals(DeviceType.PRINTER, printer)

        // TV / Media streamer
        val tv = NetworkScanner.classifyDevice(
            openPorts = listOf(8008),
            hostname = "chromecast-livingroom",
            vendor = "Google / Nest"
        )
        assertEquals(DeviceType.TV, tv)

        // Computer
        val pc = NetworkScanner.classifyDevice(
            openPorts = listOf(445),
            hostname = "desktop-win11",
            vendor = "Dell PC / Laptop"
        )
        assertEquals(DeviceType.COMPUTER, pc)

        // IoT
        val iot = NetworkScanner.classifyDevice(
            vendor = "Tuya (Smart IoT)",
            hostname = "smart-plug-01"
        )
        assertEquals(DeviceType.IOT, iot)

        // Gateway override
        val gw = NetworkScanner.classifyDevice(
            isGateway = true,
            vendor = "Apple"
        )
        assertEquals(DeviceType.ROUTER, gw)
    }

    @Test
    fun testAutoLoginQuotingSpecialChars() {
        val dangerousPass = "p@ss'w\"ord\\1\n2"
        val quoted = com.routerhub.ui.escapeJsString(dangerousPass)
        assertTrue(quoted.startsWith("\""))
        assertTrue(quoted.endsWith("\""))
        assertTrue(quoted.contains("\\\""))
        assertTrue(quoted.contains("\\\\"))
        assertTrue(quoted.contains("\\n"))
    }

    @Test
    fun testSecurityAuditScoreCalculations() {
        val passedCheck = SecurityCheckResult(
            id = "CHK_PASS",
            title = "Test",
            description = "Pass",
            severity = VulnerabilitySeverity.INFO,
            isPassed = true,
            recommendation = "None"
        )
        val failedCheck = SecurityCheckResult(
            id = "CHK_FAIL",
            title = "Test Fail",
            description = "Fail",
            severity = VulnerabilitySeverity.CRITICAL,
            isPassed = false,
            recommendation = "Fix immediately"
        )

        // 3 passed out of 4 = 75%
        val total = 4
        val passed = 3
        val score = ((passed.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
        assertEquals(75, score)

        val report = SecurityAuditReport(
            score = score,
            checks = listOf(passedCheck, passedCheck, passedCheck, failedCheck),
            summary = "Good with Recommendations"
        )
        assertEquals(75, report.score)
        assertEquals(1, report.checks.count { !it.isPassed })
    }

    @Test
    fun testSpeedTestCapabilityThresholds() {
        // High-speed 4K streaming check: >= 25 Mbps
        val fastResult = SpeedTestResult(
            peakDownloadMbps = 120f,
            peakUploadMbps = 45f,
            pingMs = 15L,
            jitterMs = 2L
        )
        assertTrue(fastResult.peakDownloadMbps >= 25f)
        assertTrue(fastResult.pingMs < 60 && fastResult.jitterMs < 20)
        assertTrue(fastResult.peakDownloadMbps >= 10f && fastResult.peakUploadMbps >= 5f)
        assertTrue(fastResult.peakUploadMbps >= 15f)

        // Slow connection check
        val slowResult = SpeedTestResult(
            peakDownloadMbps = 8f,
            peakUploadMbps = 2f,
            pingMs = 95L,
            jitterMs = 35L
        )
        assertFalse(slowResult.peakDownloadMbps >= 25f)
        assertFalse(slowResult.pingMs < 60 && slowResult.jitterMs < 20)
        assertFalse(slowResult.peakDownloadMbps >= 10f && slowResult.peakUploadMbps >= 5f)
        assertFalse(slowResult.peakUploadMbps >= 15f)
    }

    @Test
    fun testSpeedTestGoogleEngineDefaults() {
        val result = SpeedTestResult()
        assertEquals("Google Global Edge Network", result.serverName)
        assertEquals("Ready to test", result.statusText)
        assertEquals(SpeedTestPhase.IDLE, result.phase)
    }

    @Test
    fun testNetworkConnectionTypeEnum() {
        val wifi = NetworkScanner.NetworkConnectionType.WIFI
        val cellular = NetworkScanner.NetworkConnectionType.CELLULAR
        val disconnected = NetworkScanner.NetworkConnectionType.DISCONNECTED
        val other = NetworkScanner.NetworkConnectionType.OTHER

        assertEquals("WIFI", wifi.name)
        assertEquals("CELLULAR", cellular.name)
        assertEquals("DISCONNECTED", disconnected.name)
        assertEquals("OTHER", other.name)
    }
}


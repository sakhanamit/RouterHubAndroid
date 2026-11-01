package com.routerhub

import com.routerhub.model.DeviceType
import com.routerhub.model.NetworkDevice
import com.routerhub.model.RouterDevice
import com.routerhub.model.SecurityAuditReport
import com.routerhub.model.SecurityCheckResult
import com.routerhub.model.VulnerabilitySeverity
import com.routerhub.network.NetworkScanner
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
}

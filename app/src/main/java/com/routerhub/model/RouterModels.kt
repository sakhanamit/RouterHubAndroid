package com.routerhub.model

enum class DeviceType {
    ROUTER,
    PHONE,
    COMPUTER,
    TV,
    IOT,
    PRINTER,
    UNKNOWN
}

enum class VulnerabilitySeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class SecurityCheckResult(
    val id: String,
    val title: String,
    val description: String,
    val severity: VulnerabilitySeverity,
    val isPassed: Boolean,
    val target: String? = null,
    val recommendation: String
)

data class SecurityAuditReport(
    val score: Int = 100,
    val checks: List<SecurityCheckResult> = emptyList(),
    val scannedAt: Long = System.currentTimeMillis(),
    val summary: String = ""
)

data class RouterDevice(
    val id: String,
    val name: String,
    val ip: String,
    val username: String = "admin",
    val password: String = ""
)

data class NetworkDevice(
    val ip: String,
    val mac: String? = null,
    val hostname: String? = null,
    val vendor: String? = null,
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val isGateway: Boolean = false,
    val isThisDevice: Boolean = false,
    val openPorts: List<Int> = emptyList(),
    val pingMs: Long? = null,
    val httpTitle: String? = null
)


package com.routerhub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.routerhub.model.DeviceType
import com.routerhub.model.NetworkDevice
import com.routerhub.model.RouterDevice
import com.routerhub.model.SecurityAuditReport
import com.routerhub.model.SecurityCheckResult
import com.routerhub.model.VulnerabilitySeverity
import com.routerhub.network.NetworkScanner
import com.routerhub.network.SecurityAuditor
import com.routerhub.network.SpeedTestManager
import com.routerhub.network.SpeedTestPhase
import com.routerhub.network.SpeedTestResult
import com.routerhub.ui.RouterWebViewScreen
import com.routerhub.ui.SpeedTestScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install global UncaughtExceptionHandler to catch and suppress Compose OEM ACTION_HOVER_EXIT framework bug
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable is IllegalStateException && throwable.message?.contains("ACTION_HOVER_EXIT") == true) {
                // Suppress Compose hover exit exception on OEM devices safely
                return@setDefaultUncaughtExceptionHandler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        setContent {
            val isDark = isSystemInDarkTheme()

            val lightColors = lightColorScheme(
                primary = Color(0xFF007A8A),           // Brand Teal
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE2F4F6),
                onPrimaryContainer = Color(0xFF003840),
                secondary = Color(0xFF5A6265),
                secondaryContainer = Color(0xFFE6E8EA),
                onSecondaryContainer = Color(0xFF1D2021),
                background = Color(0xFFF2F2F7),        // iOS grouped background
                onBackground = Color(0xFF1C1C1E),
                surface = Color(0xFFFFFFFF),           // iOS card surface
                onSurface = Color(0xFF1C1C1E),
                surfaceVariant = Color(0xFFE5E5EA),    // iOS secondary fill
                onSurfaceVariant = Color(0xFF636366),
                outline = Color(0xFF8E8E93),
                outlineVariant = Color(0xFFE5E5EA),
                error = Color(0xFFFF3B30),
                onError = Color.White
            )

            val darkColors = darkColorScheme(
                primary = Color(0xFF22B8B8),           // Bright Teal
                onPrimary = Color(0xFF003840),
                primaryContainer = Color(0xFF004F58),
                onPrimaryContainer = Color(0xFFA2EFF7),
                secondary = Color(0xFF9E9EA0),
                secondaryContainer = Color(0xFF2C2C2E),
                onSecondaryContainer = Color(0xFFE5E5EA),
                background = Color(0xFF000000),        // iOS dark background
                onBackground = Color(0xFFFFFFFF),
                surface = Color(0xFF1C1C1E),           // iOS dark card
                onSurface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFF2C2C2E),
                onSurfaceVariant = Color(0xFFA1A1A6),
                outline = Color(0xFF8E8E93),
                outlineVariant = Color(0xFF38383A),
                error = Color(0xFFFF453A),
                onError = Color.Black
            )

            val typography = Typography(
                headlineMedium = Typography().headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                headlineSmall = Typography().headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                titleLarge = Typography().titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                ),
                titleMedium = Typography().titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp
                ),
                titleSmall = Typography().titleSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                bodyMedium = Typography().bodyMedium.copy(
                    letterSpacing = 0.1.sp
                ),
                labelMedium = Typography().labelMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                labelSmall = Typography().labelSmall.copy(
                    fontWeight = FontWeight.Medium
                )
            )

            MaterialTheme(
                colorScheme = if (isDark) darkColors else lightColors,
                typography = typography
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RouterHubMainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterHubMainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("router_hub_prefs", Context.MODE_PRIVATE) }

    var showWelcomeScreen by remember {
        mutableStateOf(!prefs.getBoolean("has_completed_welcome", false))
    }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var isPrivacyMaskEnabled by remember { mutableStateOf(false) }

    // Privacy Masking Helpers
    fun maskIp(ip: String): String = if (isPrivacyMaskEnabled) ip.replace(Regex("\\.\\d+$"), ".***") else ip
    fun maskMac(mac: String?): String? = if (isPrivacyMaskEnabled && mac != null) mac.replace(Regex(":[0-9A-FA-F]{2}:[0-9A-FA-F]{2}$"), ":**:**") else mac

    // 4 Tabs: 0 = Routers, 1 = Devices, 2 = Speed Test, 3 = Security
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeRouter by remember { mutableStateOf<RouterDevice?>(null) }
    var routers by remember { mutableStateOf(loadRouters(context)) }
    var editingRouter by remember { mutableStateOf<RouterDevice?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var prefillIp by remember { mutableStateOf("192.168.0.1") }
    var prefillName by remember { mutableStateOf("") }

    // Homepage router search & discovery state
    var routerSearchQuery by remember { mutableStateOf("") }
    var discoveredRouters by remember { mutableStateOf<List<NetworkDevice>>(emptyList()) }
    var isSearchingRouters by remember { mutableStateOf(false) }

    // Dynamic Network & Scanner state (Auto-refreshing)
    var gatewayIp by remember { mutableStateOf<String?>(null) }
    var ownIp by remember { mutableStateOf<String?>(null) }
    var currentSubnet by remember { mutableStateOf("192.168.0") }

    fun refreshNetworkInfo() {
        val (gIp, oIp) = NetworkScanner.detectGatewayAndIp(context)
        if (gIp != gatewayIp || oIp != ownIp) {
            gatewayIp = gIp
            ownIp = oIp
            val computed = (oIp ?: gIp ?: "192.168.0.1").substringBeforeLast(".", "192.168.0")
            currentSubnet = computed
        }
    }

    // Auto-refresh network IP and Subnet dynamically
    LaunchedEffect(Unit) {
        refreshNetworkInfo()
        while (true) {
            refreshNetworkInfo()
            kotlinx.coroutines.delay(3000)
        }
    }

    var showSubnetDialog by remember { mutableStateOf(false) }

    var scannedDevices by remember { mutableStateOf<List<NetworkDevice>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var scanCountText by remember { mutableStateOf("") }
    var deviceSearchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    // Speed Test State
    var speedTestResult by remember { mutableStateOf(SpeedTestResult()) }
    var isTestingSpeed by remember { mutableStateOf(false) }

    // Connectivity Status (Router Online even if internet is down!)
    var isRouterOnline by remember { mutableStateOf(true) }
    var isInternetOnline by remember { mutableStateOf(false) }

    // Check Router and Internet Connectivity
    LaunchedEffect(gatewayIp) {
        scope.launch(Dispatchers.IO) {
            val currentG = gatewayIp
            val routerReachable = if (!currentG.isNullOrBlank()) {
                try {
                    val addr = InetAddress.getByName(currentG)
                    addr.isReachable(1000) || try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(currentG, 80), 600)
                            true
                        }
                    } catch (_: Exception) {
                        true
                    }
                } catch (_: Exception) {
                    true
                }
            } else {
                true
            }

            val internetReachable = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("1.1.1.1", 53), 1000)
                    true
                }
            } catch (_: Exception) {
                false
            }

            withContext(Dispatchers.Main) {
                isRouterOnline = routerReachable
                isInternetOnline = internetReachable
            }
        }
    }

    // Security Audit state
    var securityReport by remember { mutableStateOf<SecurityAuditReport?>(null) }
    var isAuditing by remember { mutableStateOf(false) }
    var auditProgress by remember { mutableFloatStateOf(0f) }
    var auditStatusText by remember { mutableStateOf("") }

    // Function to search and discover routers on the network
    fun startRouterSearch() {
        if (isSearchingRouters) return
        scope.launch {
            isSearchingRouters = true
            refreshNetworkInfo()
            val currentGateway = gatewayIp
            if (currentGateway != null && discoveredRouters.none { it.ip == currentGateway }) {
                discoveredRouters = listOf(
                    NetworkDevice(
                        ip = currentGateway,
                        isGateway = true,
                        deviceType = DeviceType.ROUTER,
                        vendor = "Default Gateway"
                    )
                )
            }
            NetworkScanner.discoverRouters(
                subnetPrefix = currentSubnet,
                gatewayIp = gatewayIp,
                ownIp = ownIp
            ) { routerDevice ->
                discoveredRouters = (discoveredRouters.filterNot { it.ip == routerDevice.ip } + routerDevice)
                    .sortedWith(
                        compareByDescending<NetworkDevice> { it.isGateway }
                            .thenByDescending { it.deviceType == DeviceType.ROUTER }
                            .thenBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
                    )
            }
            isSearchingRouters = false
        }
    }

    // Function to trigger subnet scan
    fun startNetworkScan() {
        if (isScanning) return
        scope.launch {
            isScanning = true
            scanProgress = 0f
            scannedDevices = emptyList()
            refreshNetworkInfo()
            scanCountText = "Scanning $currentSubnet.1 - 254..."

            val results = NetworkScanner.scanSubnet(
                subnetPrefix = currentSubnet,
                gatewayIp = gatewayIp,
                ownIp = ownIp
            ) { scanned, total, foundDevice ->
                scanProgress = scanned.toFloat() / total
                scanCountText = "Scanned $scanned / $total hosts (${scannedDevices.size} active)"
                if (foundDevice != null) {
                    scannedDevices = (scannedDevices.filterNot { it.ip == foundDevice.ip } + foundDevice)
                        .sortedWith(
                            compareByDescending<NetworkDevice> { it.isGateway }
                                .thenByDescending { it.isThisDevice }
                                .thenByDescending { it.deviceType == DeviceType.ROUTER }
                                .thenBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
                        )
                    if (foundDevice.isGateway || foundDevice.deviceType == DeviceType.ROUTER) {
                        discoveredRouters = (discoveredRouters.filterNot { it.ip == foundDevice.ip } + foundDevice)
                    }
                }
            }
            scannedDevices = results
            isScanning = false
            scanCountText = "Found ${results.size} active device(s)"
        }
    }

    // Function to run Speed Test
    fun startSpeedTest() {
        if (isTestingSpeed) return
        scope.launch {
            isTestingSpeed = true
            SpeedTestManager.runSpeedTest { updatedResult ->
                speedTestResult = updatedResult
            }
            isTestingSpeed = false
        }
    }

    // Function to trigger security audit
    fun startSecurityAudit() {
        if (isAuditing) return
        scope.launch {
            isAuditing = true
            auditProgress = 0f
            auditStatusText = "Initiating vulnerability audit..."

            val report = SecurityAuditor.auditNetworkSecurity(
                context = context,
                gatewayIp = gatewayIp,
                savedRouters = routers,
                discoveredDevices = scannedDevices
            ) { progress, status ->
                auditProgress = progress
                auditStatusText = status
            }

            securityReport = report
            isAuditing = false
            auditStatusText = "Security audit complete"
        }
    }

    // Auto-search routers on launch
    LaunchedEffect(Unit) {
        startRouterSearch()
    }

    // Auto-scan devices tab on first view
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && scannedDevices.isEmpty() && !isScanning) {
            startNetworkScan()
        } else if (selectedTab == 3 && securityReport == null && !isAuditing) {
            startSecurityAudit()
        }
    }

    // If a router web portal is opened, present the full-screen WebView screen
    if (activeRouter != null) {
        RouterWebViewScreen(
            router = activeRouter!!,
            onBack = { activeRouter = null }
        )
        return
    }

    // Welcome & onboarding guide screen
    if (showWelcomeScreen) {
        WelcomeScreen(
            gatewayIp = maskIp(gatewayIp ?: "192.168.1.1"),
            ownIp = maskIp(ownIp ?: "192.168.1.100"),
            subnet = currentSubnet,
            activeDevicesCount = scannedDevices.size,
            isRouterOnline = isRouterOnline,
            isInternetOnline = isInternetOnline,
            onDismiss = {
                prefs.edit().putBoolean("has_completed_welcome", true).apply()
                showWelcomeScreen = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = "Router Hub Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                "Router Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                            Text(
                                text = if (gatewayIp != null) "Router: ${maskIp(gatewayIp!!)}" else "Wi-Fi: Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                },
                actions = {
                    // Quick Action: Web Portal when Gateway is detected, OR Add Router on Tab 0
                    val currentGateway = gatewayIp
                    if (currentGateway != null) {
                        IconButton(onClick = {
                            activeRouter = RouterDevice(
                                id = "gateway_quick",
                                name = "Primary Router",
                                ip = currentGateway
                            )
                        }) {
                            Icon(Icons.Default.Language, contentDescription = "Open Router Web", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else if (selectedTab == 0) {
                        IconButton(onClick = {
                            editingRouter = null
                            prefillIp = "192.168.0.1"
                            prefillName = ""
                            showAddDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Router")
                        }
                    }

                    // 3-Dot Overflow Menu (cleanly houses all secondary actions to keep title un-squished)
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isPrivacyMaskEnabled) "Privacy Masking (Active)" else "Privacy Masking (Off)") },
                                onClick = {
                                    isPrivacyMaskEnabled = !isPrivacyMaskEnabled
                                    showTopMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isPrivacyMaskEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = if (isPrivacyMaskEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                            if (selectedTab == 0) {
                                DropdownMenuItem(
                                    text = { Text("Add Router Profile") },
                                    onClick = {
                                        showTopMenu = false
                                        editingRouter = null
                                        prefillIp = gatewayIp ?: "192.168.0.1"
                                        prefillName = ""
                                        showAddDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rescan Local Routers") },
                                    onClick = {
                                        showTopMenu = false
                                        startRouterSearch()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Check App Updates") },
                                onClick = {
                                    showTopMenu = false
                                    showUpdateDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Welcome & Guide") },
                                onClick = {
                                    showTopMenu = false
                                    showWelcomeScreen = true
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                // Tab 0: Routers
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (routers.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) { Text("${routers.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Router, contentDescription = "Routers")
                        }
                    },
                    label = { Text("Routers", maxLines = 1, softWrap = false) }
                )

                // Tab 1: Devices
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (scannedDevices.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) { Text("${scannedDevices.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = "Devices")
                        }
                    },
                    label = { Text("Devices", maxLines = 1, softWrap = false) }
                )

                // Tab 2: Speed Test
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(Icons.Default.Speed, contentDescription = "Speed Test")
                    },
                    label = { Text("Speed Test", maxLines = 1, softWrap = false) }
                )

                // Tab 3: Security & Vulnerabilities
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        val vulnCount = securityReport?.checks?.count { !it.isPassed } ?: 0
                        BadgedBox(
                            badge = {
                                if (vulnCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) { Text("$vulnCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Security, contentDescription = "Security")
                        }
                    },
                    label = { Text("Security", maxLines = 1, softWrap = false) }
                )
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                1 -> {
                    FloatingActionButton(
                        onClick = { startNetworkScan() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Radar, contentDescription = "Scan Devices", modifier = Modifier.size(22.dp))
                        }
                    }
                }
                else -> {}
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 200),
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    0 -> {
                        RoutersTabContent(
                            gatewayIp = gatewayIp,
                            routers = routers,
                            discoveredRouters = discoveredRouters,
                            isSearchingRouters = isSearchingRouters,
                            searchQuery = routerSearchQuery,
                            maskIp = ::maskIp,
                            onSearchQueryChange = { routerSearchQuery = it },
                            onRefreshRouters = { startRouterSearch() },
                            onCheckUpdate = { showUpdateDialog = true },
                            onOpenRouter = { router -> activeRouter = router },
                            onEditRouter = { router ->
                                editingRouter = router
                                prefillIp = router.ip
                                prefillName = router.name
                                showAddDialog = true
                            },
                            onDeleteRouter = { router ->
                                routers = routers.filterNot { it.id == router.id }
                                saveRouters(context, routers)
                            },
                            onSaveGateway = { ip ->
                                editingRouter = null
                                prefillIp = ip
                                prefillName = "Primary Gateway"
                                showAddDialog = true
                            },
                            onAddDiscoveredRouter = { device ->
                                editingRouter = null
                                prefillIp = device.ip
                                prefillName = when {
                                    !device.httpTitle.isNullOrBlank() -> device.httpTitle
                                    device.isGateway -> "Primary Gateway"
                                    !device.vendor.isNullOrBlank() && device.vendor != "Unknown" -> "${device.vendor} Router"
                                    else -> "Router (${maskIp(device.ip)})"
                                }
                                showAddDialog = true
                            },
                            onSwitchToScanner = { selectedTab = 1 },
                            onAddNewRouter = {
                                editingRouter = null
                                prefillIp = gatewayIp ?: "192.168.0.1"
                                prefillName = ""
                                showAddDialog = true
                            }
                        )
                    }
                    1 -> {
                        ScannerTabContent(
                            currentSubnet = currentSubnet,
                            gatewayIp = gatewayIp,
                            ownIp = ownIp,
                            devices = scannedDevices,
                            isScanning = isScanning,
                            scanProgress = scanProgress,
                            statusText = scanCountText,
                            searchQuery = deviceSearchQuery,
                            maskIp = ::maskIp,
                            maskMac = ::maskMac,
                            onSearchQueryChange = { deviceSearchQuery = it },
                            selectedFilter = selectedFilter,
                            onFilterSelect = { selectedFilter = it },
                            onChangeSubnetClick = { showSubnetDialog = true },
                            onOpenDeviceWeb = { ip ->
                                activeRouter = RouterDevice(
                                    id = "discovered_${System.currentTimeMillis()}",
                                    name = "Device Web (${maskIp(ip)})",
                                    ip = ip
                                )
                            },
                            onSaveDeviceAsRouter = { device ->
                                editingRouter = null
                                prefillIp = device.ip
                                showAddDialog = true
                            }
                        )
                    }
                    2 -> {
                        SpeedTestScreen(
                            result = speedTestResult,
                            isRunning = isTestingSpeed,
                            onStartTest = { startSpeedTest() }
                        )
                    }
                    3 -> {
                        SecurityTabContent(
                            report = securityReport,
                            isAuditing = isAuditing,
                            progress = auditProgress,
                            statusText = auditStatusText,
                            onStartAudit = { startSecurityAudit() }
                        )
                    }
                }
            }
        }
    }

    // App Update Dialog linked to GitHub Pages
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("RouterHub Updates", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Installed Version: v1.2 (Build 3)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("Check for the latest release, changelogs, and APK downloads on our official GitHub Pages site.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "https://sakhanamit.github.io/RouterHubAndroid/",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sakhanamit.github.io/RouterHubAndroid/"))
                        context.startActivity(intent)
                        showUpdateDialog = false
                    }
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Visit Webpage")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Add / Edit Router Dialog
    if (showAddDialog) {
        AddEditRouterDialog(
            initial = editingRouter,
            defaultIp = prefillIp,
            defaultName = prefillName,
            onDismiss = { showAddDialog = false },
            onSave = { updated ->
                routers = if (editingRouter == null) {
                    routers + updated
                } else {
                    routers.map { if (it.id == updated.id) updated else it }
                }
                saveRouters(context, routers)
                showAddDialog = false
            }
        )
    }

    // Change Subnet Dialog
    if (showSubnetDialog) {
        ChangeSubnetDialog(
            initialSubnet = currentSubnet,
            onDismiss = { showSubnetDialog = false },
            onConfirm = { newSubnet ->
                currentSubnet = newSubnet
                showSubnetDialog = false
                startNetworkScan()
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 0: ROUTERS TAB CONTENT
// -------------------------------------------------------------

@Composable
fun RoutersTabContent(
    gatewayIp: String?,
    routers: List<RouterDevice>,
    discoveredRouters: List<NetworkDevice>,
    isSearchingRouters: Boolean,
    searchQuery: String,
    maskIp: (String) -> String,
    onSearchQueryChange: (String) -> Unit,
    onRefreshRouters: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenRouter: (RouterDevice) -> Unit,
    onEditRouter: (RouterDevice) -> Unit,
    onDeleteRouter: (RouterDevice) -> Unit,
    onSaveGateway: (String) -> Unit,
    onAddDiscoveredRouter: (NetworkDevice) -> Unit,
    onSwitchToScanner: () -> Unit,
    onAddNewRouter: () -> Unit
) {
    val context = LocalContext.current

    val filteredSaved = remember(routers, searchQuery) {
        routers.filter {
            searchQuery.isBlank() ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.ip.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredDiscovered = remember(discoveredRouters, searchQuery) {
        discoveredRouters.filter {
            searchQuery.isBlank() ||
                it.ip.contains(searchQuery, ignoreCase = true) ||
                (it.vendor != null && it.vendor.contains(searchQuery, ignoreCase = true)) ||
                (it.httpTitle != null && it.httpTitle.contains(searchQuery, ignoreCase = true))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 120.dp)
    ) {
        // Active Gateway Banner Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (gatewayIp != null) {
                            onOpenRouter(
                                RouterDevice(
                                    id = "active_gateway",
                                    name = "Active Gateway Router",
                                    ip = gatewayIp
                                )
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Router,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Connected Gateway",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981)
                            ) {
                                Text(
                                    "ONLINE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (gatewayIp != null) "IP: ${maskIp(gatewayIp)}" else "Wi-Fi Gateway Not Found",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (gatewayIp != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Open",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Section Title: Discovered Routers
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DISCOVERED ROUTERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isSearchingRouters) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "${filteredDiscovered.size} Found",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (filteredDiscovered.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isSearchingRouters) "Scanning subnet for Wi-Fi routers..." else "No other routers found on subnet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredDiscovered, key = { "disc_${it.ip}" }) { device ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenRouter(
                                RouterDevice(
                                    id = "discovered_${device.ip}",
                                    name = device.httpTitle ?: "${device.vendor ?: "Router"} (${maskIp(device.ip)})",
                                    ip = device.ip
                                )
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val title = when {
                                device.isGateway -> device.httpTitle ?: "Primary Gateway Router"
                                !device.httpTitle.isNullOrBlank() -> device.httpTitle
                                !device.vendor.isNullOrBlank() && !device.vendor.contains("Smartphone", ignoreCase = true) -> device.vendor
                                else -> "Router Host"
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "IP: ${maskIp(device.ip)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { onAddDiscoveredRouter(device) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Save", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Section Title: Saved Profiles
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SAVED PROFILES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${filteredSaved.size} Saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (filteredSaved.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No Saved Router Profiles", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add your router profile to save custom login credentials securely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onAddNewRouter, shape = RoundedCornerShape(10.dp)) {
                            Text("Add Router Profile")
                        }
                    }
                }
            }
        } else {
            items(filteredSaved, key = { it.id }) { router ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRouter(router) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(router.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("IP: ${maskIp(router.ip)}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onEditRouter(router) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteRouter(router) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: NETWORK SCANNER UI (Categorized Devices + Protocol Badges)
// -------------------------------------------------------------

@Composable
fun ScannerTabContent(
    currentSubnet: String,
    gatewayIp: String?,
    ownIp: String?,
    devices: List<NetworkDevice>,
    isScanning: Boolean,
    scanProgress: Float,
    statusText: String,
    searchQuery: String,
    maskIp: (String) -> String,
    maskMac: (String?) -> String?,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: String,
    onFilterSelect: (String) -> Unit,
    onChangeSubnetClick: () -> Unit,
    onOpenDeviceWeb: (String) -> Unit,
    onSaveDeviceAsRouter: (NetworkDevice) -> Unit
) {
    val context = LocalContext.current

    val filteredDevices = remember(devices, searchQuery, selectedFilter) {
        devices.filter { device ->
            val matchesSearch = searchQuery.isBlank() ||
                device.ip.contains(searchQuery, ignoreCase = true) ||
                (device.brand != null && device.brand.contains(searchQuery, ignoreCase = true)) ||
                (device.model != null && device.model.contains(searchQuery, ignoreCase = true)) ||
                (device.mac != null && device.mac.contains(searchQuery, ignoreCase = true)) ||
                (device.hostname != null && device.hostname.contains(searchQuery, ignoreCase = true)) ||
                (device.vendor != null && device.vendor.contains(searchQuery, ignoreCase = true)) ||
                (device.httpTitle != null && device.httpTitle.contains(searchQuery, ignoreCase = true))

            val matchesFilter = when (selectedFilter) {
                "Routers" -> device.deviceType == DeviceType.ROUTER || device.isGateway
                "Phones" -> device.deviceType == DeviceType.PHONE || device.isThisDevice
                "Computers" -> device.deviceType == DeviceType.COMPUTER
                "IoT/TV" -> device.deviceType == DeviceType.IOT || device.deviceType == DeviceType.TV || device.deviceType == DeviceType.PRINTER || device.deviceType == DeviceType.CAMERA || device.deviceType == DeviceType.STORAGE
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    val categorizedMap = remember(filteredDevices) {
        filteredDevices.groupBy { device ->
            when {
                device.isGateway || device.deviceType == DeviceType.ROUTER -> "📡 Gateways & Routers"
                device.isThisDevice || device.deviceType == DeviceType.PHONE -> "📱 Smartphones & Tablets"
                device.deviceType == DeviceType.COMPUTER -> "💻 Computers & Laptops"
                device.deviceType == DeviceType.TV -> "📺 Smart TVs & Media"
                device.deviceType == DeviceType.IOT -> "🔌 Smart Home & IoT"
                device.deviceType == DeviceType.PRINTER || device.deviceType == DeviceType.CAMERA -> "🖨️ Printers & Cameras"
                device.deviceType == DeviceType.STORAGE -> "💾 Network Storage"
                else -> "❓ Other Devices"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 120.dp)
    ) {
        // Subnet Overview Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Subnet: $currentSubnet.0/24",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "GW: ${if (gatewayIp != null) maskIp(gatewayIp) else "N/A"} • IP: ${if (ownIp != null) maskIp(ownIp) else "N/A"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onChangeSubnetClick,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Edit", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                        }
                    }

                    if (isScanning) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { scanProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    if (statusText.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Compact Search Bar
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search by make, model, brand, IP, MAC...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Segmented Category Filter Pills
        item {
            val filterOptions = listOf("All", "Routers", "Phones", "Computers", "IoT/TV")
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filterOptions.forEach { filter ->
                        val isSelected = (selectedFilter == filter)
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onFilterSelect(filter) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    filter,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filteredDevices.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(14.dp))
                            Text("Scanning subnet hosts...", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        } else {
                            Icon(
                                Icons.Default.DevicesOther,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No Devices Found", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Ensure you are connected to local Wi-Fi or tap Scan Devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            categorizedMap.forEach { (categoryName, groupDevices) ->
                item(key = "header_$categoryName") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            categoryName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text("${groupDevices.size}")
                        }
                    }
                }

                items(groupDevices, key = { it.ip }) { device ->
                    IOSDeviceCardItem(
                        device = device,
                        maskIp = maskIp,
                        maskMac = maskMac,
                        onCopy = { label, text -> copyText(context, label, text) },
                        onOpenWeb = { onOpenDeviceWeb(device.ip) },
                        onSave = { onSaveDeviceAsRouter(device) }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DEVICE CARD ITEM (Protocol Icons + Color-coded Response Times)
// -------------------------------------------------------------

@Composable
fun IOSDeviceCardItem(
    device: NetworkDevice,
    maskIp: (String) -> String,
    maskMac: (String?) -> String?,
    onCopy: (String, String) -> Unit,
    onOpenWeb: () -> Unit,
    onSave: () -> Unit
) {
    val hasWeb = device.openPorts.contains(80) || device.openPorts.contains(443) || device.openPorts.contains(8080) || device.isGateway

    val pingMs = device.pingMs
    val (pingBgColor, pingTextColor, pingLabel) = when {
        pingMs == null -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "-- ms")
        pingMs <= 25 -> Triple(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981), "⚡ ${pingMs}ms")
        pingMs <= 100 -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B), "📶 ${pingMs}ms")
        else -> Triple(Color(0xFFF43F5E).copy(alpha = 0.15f), Color(0xFFF43F5E), "🐢 ${pingMs}ms")
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = if (device.isGateway) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(enabled = hasWeb) { onOpenWeb() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon: ImageVector = when (device.deviceType) {
                    DeviceType.ROUTER -> Icons.Default.Router
                    DeviceType.PHONE -> Icons.Default.PhoneIphone
                    DeviceType.COMPUTER -> Icons.Default.Laptop
                    DeviceType.TV -> Icons.Default.Tv
                    DeviceType.IOT -> Icons.Default.Sensors
                    DeviceType.PRINTER -> Icons.Default.Print
                    DeviceType.CAMERA -> Icons.Default.Videocam
                    DeviceType.STORAGE -> Icons.Default.Storage
                    DeviceType.UNKNOWN -> Icons.Default.Devices
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (device.isGateway) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (device.isGateway) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val modelName = when {
                        device.isGateway -> device.model ?: device.httpTitle ?: "Primary Gateway Router"
                        device.isThisDevice -> "This Device (${Build.MODEL})"
                        device.model != null -> device.model
                        device.httpTitle != null -> device.httpTitle
                        device.hostname != null -> device.hostname
                        device.vendor != null && device.vendor != "Network Client" -> device.vendor
                        else -> "Network Device"
                    }
                    val brandName = if (device.isGateway && (
                        device.brand?.contains("Private MAC", ignoreCase = true) == true ||
                        device.vendor?.contains("Private MAC", ignoreCase = true) == true ||
                        device.brand?.contains("Smartphone", ignoreCase = true) == true ||
                        device.vendor?.contains("Smartphone", ignoreCase = true) == true
                    )) {
                        null
                    } else {
                        device.brand ?: device.vendor
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (device.isGateway) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    "GATEWAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (brandName != null && brandName != "Network Client" && brandName != modelName && !(device.isGateway && (brandName.contains("Smartphone", ignoreCase = true) || brandName.contains("PC", ignoreCase = true)))) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    brandName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "IP: ${maskIp(device.ip)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = pingBgColor
                ) {
                    Text(
                        pingLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = pingTextColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (device.mac != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "MAC: ${maskMac(device.mac)}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onCopy("MAC Address", device.mac) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy MAC", modifier = Modifier.size(15.dp))
                        }
                    }
                }

                if (device.openPorts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Protocols: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.width(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            device.openPorts.take(6).forEach { port ->
                                ProtocolBadgeItem(port = port)
                            }
                        }
                    }
                }
            }

            if (hasWeb) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Save Profile",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = onOpenWeb,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Web Admin",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProtocolBadgeItem(port: Int) {
    val (label, icon, color) = when (port) {
        80 -> Triple("HTTP", Icons.Default.Language, Color(0xFF007A8A))
        443 -> Triple("HTTPS", Icons.Default.Lock, Color(0xFF10B981))
        22 -> Triple("SSH", Icons.Default.Code, Color(0xFF6366F1))
        23 -> Triple("TELNET", Icons.Default.Warning, Color(0xFFF43F5E))
        445, 139 -> Triple("SMB", Icons.Default.Folder, Color(0xFF8B5CF6))
        554 -> Triple("RTSP", Icons.Default.Videocam, Color(0xFFF59E0B))
        8080, 8443 -> Triple("WEB", Icons.Default.AdminPanelSettings, Color(0xFF06B6D4))
        else -> Triple("P:$port", Icons.Default.Settings, MaterialTheme.colorScheme.secondary)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// -------------------------------------------------------------
// TAB 3: SECURITY TAB CONTENT (Dedicated Rescan Button)
// -------------------------------------------------------------

@Composable
fun SecurityTabContent(
    report: SecurityAuditReport?,
    isAuditing: Boolean,
    progress: Float,
    statusText: String,
    onStartAudit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 120.dp)
    ) {
        // Security Score Hero Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val score = report?.score ?: 100
                    val scoreColor = when {
                        score >= 85 -> Color(0xFF34C759)
                        score >= 65 -> Color(0xFFFF9500)
                        else -> Color(0xFFFF3B30)
                    }

                    val animatedScore by animateFloatAsState(
                        targetValue = score.toFloat(),
                        animationSpec = tween(durationMillis = 800),
                        label = "score"
                    )

                    Box(
                        modifier = Modifier.size(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedScore / 100f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 9.dp,
                            color = scoreColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${animatedScore.toInt()}%",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                "SCORE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        when {
                            report == null -> "Network Not Yet Audited"
                            score >= 85 -> "Network Security Strong"
                            score >= 65 -> "Moderate Security Risks Detected"
                            else -> "Critical Vulnerabilities Detected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        report?.summary ?: "Audit your router and subnet for cleartext credentials, open legacy ports, and UPnP risks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (isAuditing) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    if (statusText.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Dedicated Rescan Button
                    Button(
                        onClick = onStartAudit,
                        enabled = !isAuditing,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isAuditing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Auditing Network Security...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("RE-RUN SECURITY AUDIT", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section Title: Audit Checks
        if (report != null && report.checks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SECURITY CHECKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )
                    val issuesCount = report.checks.count { !it.isPassed }
                    Badge(
                        containerColor = if (issuesCount > 0) MaterialTheme.colorScheme.error else Color(0xFF34C759),
                        contentColor = Color.White
                    ) {
                        Text(if (issuesCount > 0) "$issuesCount Risk(s)" else "All Passed", maxLines = 1, softWrap = false)
                    }
                }
            }

            items(report.checks, key = { it.id }) { check ->
                SecurityCheckCardItem(check = check)
            }
        }
    }
}

@Composable
fun SecurityCheckCardItem(check: SecurityCheckResult) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = if (check.isPassed) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (check.isPassed) Color(0xFF34C759).copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.errorContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (check.isPassed) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (check.isPassed) Color(0xFF34C759) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(check.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (check.target != null) {
                        Text("Target: ${check.target}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (check.severity) {
                        VulnerabilitySeverity.CRITICAL -> Color(0xFFFF3B30)
                        VulnerabilitySeverity.HIGH -> Color(0xFFFF9500)
                        VulnerabilitySeverity.MEDIUM -> Color(0xFFFFCC00)
                        VulnerabilitySeverity.LOW -> Color(0xFF5AC8FA)
                        VulnerabilitySeverity.INFO -> Color(0xFF8E8E93)
                    }
                ) {
                    Text(
                        check.severity.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(check.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!check.isPassed && check.recommendation.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            check.recommendation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DIALOGS
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRouterDialog(
    initial: RouterDevice?,
    defaultIp: String,
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (RouterDevice) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: defaultName.ifBlank { "My Router" }) }
    var ip by remember { mutableStateOf(initial?.ip ?: defaultIp) }
    var username by remember { mutableStateOf(initial?.username ?: "admin") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "Add Router Profile" else "Edit Router Profile",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP Address / Hostname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Admin Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Admin Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        RouterDevice(
                            id = initial?.id ?: "router_${System.currentTimeMillis()}",
                            name = name.ifBlank { "Router ($ip)" },
                            ip = ip.trim(),
                            username = username.trim(),
                            password = password
                        )
                    )
                }
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChangeSubnetDialog(
    initialSubnet: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialSubnet) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Subnet Range", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter the first 3 octets of the subnet (e.g., 192.168.1):", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Subnet Prefix") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.trim()) }) {
                Text("Scan Subnet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// -------------------------------------------------------------
// WELCOME & ONBOARDING SCREEN (Dynamic Network Summary)
// -------------------------------------------------------------

@Composable
fun WelcomeScreen(
    gatewayIp: String?,
    ownIp: String?,
    subnet: String,
    activeDevicesCount: Int,
    isRouterOnline: Boolean,
    isInternetOnline: Boolean,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable { onDismiss() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            "Get Started",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // App Icon with glow container
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(26.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "RouterHub Icon",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Welcome to RouterHub",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Your smart command center for router management, network discovery, speed testing, and security auditing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // DYNAMIC NETWORK SUMMARY CARD
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        0.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "LIVE NETWORK SUMMARY",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Wi-Fi Router Gateway:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isRouterOnline) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF43F5E).copy(alpha = 0.15f)
                        ) {
                            Text(
                                if (isRouterOnline) "ONLINE ($gatewayIp)" else "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isRouterOnline) Color(0xFF10B981) else Color(0xFFF43F5E),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Internet WAN Status:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isInternetOnline) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                        ) {
                            Text(
                                if (isInternetOnline) "CONNECTED" else "LOCAL ONLY (NO WAN)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isInternetOnline) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Discovered Devices:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$activeDevicesCount Device(s) Active",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subnet & Phone IP:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$subnet.0/24 ($ownIp)",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Feature Highlights list
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WelcomeFeatureItem(
                    icon = Icons.Default.Router,
                    title = "Instant Router Discovery",
                    description = "Detects your Wi-Fi gateway immediately and scans for other connected routers and access points."
                )

                WelcomeFeatureItem(
                    icon = Icons.Default.Devices,
                    title = "Categorized Device Identification",
                    description = "Identifies phones, laptops, smart TVs, cameras, printers, and IoT by brand, model, open ports, and color-coded latency."
                )

                WelcomeFeatureItem(
                    icon = Icons.Default.Speed,
                    title = "Speedometer Gauge Test",
                    description = "Measure download and upload bandwidth speeds, ping latency, and jitter variance in real-time."
                )

                WelcomeFeatureItem(
                    icon = Icons.Default.Shield,
                    title = "Dedicated Security Audit",
                    description = "Scans for open telnet ports, unencrypted HTTP management, and weak default passwords with dedicated rescan."
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WelcomeFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun getSecurePrefs(context: Context) = EncryptedSharedPreferences.create(
    context,
    "secure_router_prefs",
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

private fun saveRouters(context: Context, list: List<RouterDevice>) {
    val prefs = getSecurePrefs(context)
    val array = JSONArray()
    list.forEach { r ->
        val obj = JSONObject()
        obj.put("id", r.id)
        obj.put("name", r.name)
        obj.put("ip", r.ip)
        obj.put("username", r.username)
        obj.put("password", r.password)
        array.put(obj)
    }
    prefs.edit().putString("saved_routers", array.toString()).apply()
}

private fun loadRouters(context: Context): List<RouterDevice> {
    val list = mutableListOf<RouterDevice>()
    try {
        val prefs = getSecurePrefs(context)
        val str = prefs.getString("saved_routers", null) ?: return emptyList()
        val array = JSONArray(str)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                RouterDevice(
                    id = obj.optString("id", "r_$i"),
                    name = obj.optString("name", "Router"),
                    ip = obj.optString("ip", "192.168.0.1"),
                    username = obj.optString("username", "admin"),
                    password = obj.optString("password", "")
                )
            )
        }
    } catch (_: Exception) {
        // Fallback
    }
    return list
}

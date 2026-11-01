package com.routerhub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.routerhub.ui.RouterWebViewScreen
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = isSystemInDarkTheme()

            // iOS-inspired Color Schemes with native system theme following
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

    var selectedTab by remember { mutableIntStateOf(0) }
    var activeRouter by remember { mutableStateOf<RouterDevice?>(null) }
    var routers by remember { mutableStateOf(loadRouters(context)) }
    var editingRouter by remember { mutableStateOf<RouterDevice?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var prefillIp by remember { mutableStateOf("192.168.0.1") }

    // Network & Scanner state
    val networkInfo = remember { NetworkScanner.detectGatewayAndIp(context) }
    val gatewayIp = networkInfo.first
    val ownIp = networkInfo.second
    val detectedSubnet = remember(gatewayIp, ownIp) {
        (ownIp ?: gatewayIp ?: "192.168.0.1").substringBeforeLast(".", "192.168.0")
    }

    var currentSubnet by remember { mutableStateOf(detectedSubnet) }
    var showSubnetDialog by remember { mutableStateOf(false) }

    var scannedDevices by remember { mutableStateOf<List<NetworkDevice>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var scanCountText by remember { mutableStateOf("") }
    var deviceSearchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    // Security Audit state
    var securityReport by remember { mutableStateOf<SecurityAuditReport?>(null) }
    var isAuditing by remember { mutableStateOf(false) }
    var auditProgress by remember { mutableFloatStateOf(0f) }
    var auditStatusText by remember { mutableStateOf("") }

    // Function to trigger subnet scan
    fun startNetworkScan() {
        if (isScanning) return
        scope.launch {
            isScanning = true
            scanProgress = 0f
            scannedDevices = emptyList()
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
                }
            }
            scannedDevices = results
            isScanning = false
            scanCountText = "Found ${results.size} active device(s)"
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

    // Auto-scan devices tab on first view
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && scannedDevices.isEmpty() && !isScanning) {
            startNetworkScan()
        } else if (selectedTab == 2 && securityReport == null && !isAuditing) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = "Router Hub Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Router Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = if (gatewayIp != null) "Gateway: $gatewayIp" else "Wi-Fi: Not Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        // iOS-Style Top Bar "+" Add Router button
                        IconButton(onClick = {
                            editingRouter = null
                            prefillIp = gatewayIp ?: "192.168.0.1"
                            showAddDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Router")
                        }
                    }
                    if (gatewayIp != null) {
                        IconButton(onClick = {
                            activeRouter = RouterDevice(
                                id = "gateway_quick",
                                name = "Primary Router",
                                ip = gatewayIp
                            )
                        }) {
                            Icon(Icons.Default.Language, contentDescription = "Open Router Web")
                        }
                    }
                    if (selectedTab == 1) {
                        IconButton(onClick = { startNetworkScan() }, enabled = !isScanning) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                        }
                    } else if (selectedTab == 2) {
                        IconButton(onClick = { startSecurityAudit() }, enabled = !isAuditing) {
                            Icon(Icons.Default.Shield, contentDescription = "Run Audit")
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

                // Tab 2: Security & Vulnerabilities
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
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
        // Compact, unobtrusive circular FABs that do not obstruct the home screen
        floatingActionButton = {
            when (selectedTab) {
                0 -> {
                    FloatingActionButton(
                        onClick = {
                            editingRouter = null
                            prefillIp = gatewayIp ?: "192.168.0.1"
                            showAddDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Router", modifier = Modifier.size(24.dp))
                    }
                }
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
                2 -> {
                    FloatingActionButton(
                        onClick = { startSecurityAudit() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp)
                    ) {
                        if (isAuditing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Shield, contentDescription = "Audit Network", modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> {
                    RoutersTabContent(
                        gatewayIp = gatewayIp,
                        routers = routers,
                        onOpenRouter = { router -> activeRouter = router },
                        onEditRouter = { router ->
                            editingRouter = router
                            prefillIp = router.ip
                            showAddDialog = true
                        },
                        onDeleteRouter = { router ->
                            routers = routers.filterNot { it.id == router.id }
                            saveRouters(context, routers)
                        },
                        onSaveGateway = { ip ->
                            editingRouter = null
                            prefillIp = ip
                            showAddDialog = true
                        },
                        onSwitchToScanner = { selectedTab = 1 },
                        onAddNewRouter = {
                            editingRouter = null
                            prefillIp = gatewayIp ?: "192.168.0.1"
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
                        onSearchQueryChange = { deviceSearchQuery = it },
                        selectedFilter = selectedFilter,
                        onFilterSelect = { selectedFilter = it },
                        onChangeSubnetClick = { showSubnetDialog = true },
                        onOpenDeviceWeb = { ip ->
                            activeRouter = RouterDevice(
                                id = "discovered_${System.currentTimeMillis()}",
                                name = "Device Web ($ip)",
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

    // Add / Edit Router Dialog
    if (showAddDialog) {
        AddEditRouterDialog(
            initial = editingRouter,
            defaultIp = prefillIp,
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
// TAB 0: MY ROUTERS UI (iOS Grouped Inset Style)
// -------------------------------------------------------------

@Composable
fun RoutersTabContent(
    gatewayIp: String?,
    routers: List<RouterDevice>,
    onOpenRouter: (RouterDevice) -> Unit,
    onEditRouter: (RouterDevice) -> Unit,
    onDeleteRouter: (RouterDevice) -> Unit,
    onSaveGateway: (String) -> Unit,
    onSwitchToScanner: () -> Unit,
    onAddNewRouter: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp)
    ) {
        // Active Gateway Hero Card
        if (gatewayIp != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            onOpenRouter(
                                RouterDevice(
                                    id = "active_gateway",
                                    name = "Primary Router",
                                    ip = gatewayIp
                                )
                            )
                        }
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Connected Gateway",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    gatewayIp,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF34C759),
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Online",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF34C759),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Responsive 50/50 Button Row (Guarantees horizontal text)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSaveGateway(gatewayIp) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Save Profile",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = {
                                    onOpenRouter(
                                        RouterDevice(
                                            id = "active_gateway",
                                            name = "Primary Router",
                                            ip = gatewayIp
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Open Admin",
                                    style = MaterialTheme.typography.labelMedium,
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

        // Section Title: Saved Routers
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SAVED ROUTERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text("${routers.size}")
                }
            }
        }

        if (routers.isEmpty()) {
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
                            .padding(28.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = "Router Hub",
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "No Saved Routers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add your TP-Link, OpenWrt, or home router to quickly access web administration with auto-login.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onAddNewRouter, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Router", maxLines = 1, softWrap = false)
                            }
                            Button(onClick = onSwitchToScanner, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Scan Subnet", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        } else {
            // Grouped Inset Card for Saved Routers
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Column {
                        routers.forEachIndexed { index, router ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenRouter(router) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Router,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            router.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (router.username.isNotBlank()) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    router.username,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        router.ip,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { copyText(context, "Router IP", router.ip) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onEditRouter(router) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteRouter(router) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            if (index < routers.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 68.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: NETWORK SCANNER UI
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
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: String,
    onFilterSelect: (String) -> Unit,
    onChangeSubnetClick: () -> Unit,
    onOpenDeviceWeb: (String) -> Unit,
    onSaveDeviceAsRouter: (NetworkDevice) -> Unit
) {
    val context = LocalContext.current

    // Filter devices
    val filteredDevices = remember(devices, searchQuery, selectedFilter) {
        devices.filter { device ->
            val matchesSearch = searchQuery.isBlank() ||
                device.ip.contains(searchQuery, ignoreCase = true) ||
                (device.mac != null && device.mac.contains(searchQuery, ignoreCase = true)) ||
                (device.hostname != null && device.hostname.contains(searchQuery, ignoreCase = true)) ||
                (device.vendor != null && device.vendor.contains(searchQuery, ignoreCase = true)) ||
                (device.httpTitle != null && device.httpTitle.contains(searchQuery, ignoreCase = true))

            val matchesFilter = when (selectedFilter) {
                "Routers" -> device.deviceType == DeviceType.ROUTER || device.isGateway
                "Phones" -> device.deviceType == DeviceType.PHONE || device.isThisDevice
                "Computers" -> device.deviceType == DeviceType.COMPUTER
                "IoT/TV" -> device.deviceType == DeviceType.IOT || device.deviceType == DeviceType.TV || device.deviceType == DeviceType.PRINTER
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp)
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
                                softWrap = false
                            )
                            Text(
                                "Gateway: ${gatewayIp ?: "N/A"}  •  Phone: ${ownIp ?: "N/A"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        OutlinedButton(
                            onClick = onChangeSubnetClick,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Subnet", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
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

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("Search IP, MAC, or Device name...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        // iOS Segmented Control Bar (Filter pills)
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

        // Section Title: Devices List
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DISCOVERED DEVICES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text("${filteredDevices.size}")
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
            items(filteredDevices, key = { it.ip }) { device ->
                IOSDeviceCardItem(
                    device = device,
                    onCopy = { label, text -> copyText(context, label, text) },
                    onOpenWeb = { onOpenDeviceWeb(device.ip) },
                    onSave = { onSaveDeviceAsRouter(device) }
                )
            }
        }
    }
}

@Composable
fun IOSDeviceCardItem(
    device: NetworkDevice,
    onCopy: (String, String) -> Unit,
    onOpenWeb: () -> Unit,
    onSave: () -> Unit
) {
    val hasWeb = device.openPorts.contains(80) || device.openPorts.contains(443) || device.openPorts.contains(8080) || device.isGateway
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
                    // Smart Display Name Resolution
                    val displayName = when {
                        device.isGateway -> "Router Gateway (${device.httpTitle ?: device.vendor ?: "Router"})"
                        device.isThisDevice -> "This Device (${Build.MODEL})"
                        device.httpTitle != null -> device.httpTitle
                        device.hostname != null -> device.hostname
                        device.vendor != null && device.vendor != "Network Client" -> device.vendor
                        else -> "Network Client"
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
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
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "IP: ${device.ip}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (device.pingMs != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF34C759).copy(alpha = 0.15f)
                    ) {
                        Text(
                            "${device.pingMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF34C759),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            // MAC & Vendor identification info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (device.mac != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "MAC: ${device.mac}",
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

                if (device.vendor != null) {
                    Text(
                        "Vendor: ${device.vendor}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (device.openPorts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Open: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            softWrap = false
                        )
                        device.openPorts.forEach { port ->
                            val name = when (port) {
                                80 -> "HTTP:80"
                                443 -> "HTTPS:443"
                                22 -> "SSH:22"
                                445 -> "SMB:445"
                                8080 -> "Web:8080"
                                else -> "$port"
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Actions row (Responsive 50/50 layout)
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

// -------------------------------------------------------------
// TAB 2: SECURITY & VULNERABILITY SCANNER (iOS Grouped Style)
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
        contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp)
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

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = onStartAudit,
                        enabled = !isAuditing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isAuditing) "Scanning Risks..." else "Scan Security Risks",
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = FontWeight.SemiBold
                        )
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
                        maxLines = 1,
                        softWrap = false,
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
    var expanded by remember { mutableStateOf(!check.isPassed) }

    val statusColor = if (check.isPassed) {
        Color(0xFF34C759)
    } else {
        when (check.severity) {
            VulnerabilitySeverity.CRITICAL, VulnerabilitySeverity.HIGH -> Color(0xFFFF3B30)
            VulnerabilitySeverity.MEDIUM -> Color(0xFFFF9500)
            VulnerabilitySeverity.LOW -> Color(0xFFFFCC00)
            VulnerabilitySeverity.INFO -> Color(0xFF8E8E93)
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = if (!check.isPassed) statusColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (check.isPassed) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        check.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (check.target != null) {
                        Text(
                            "Target: ${check.target}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        if (check.isPassed) "PASSED" else check.severity.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))

                    Text(
                        check.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Fix: ${check.recommendation}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DIALOGS & HELPERS (iOS Rounded Modal Style)
// -------------------------------------------------------------

@Composable
fun AddEditRouterDialog(
    initial: RouterDevice?,
    defaultIp: String,
    onDismiss: () -> Unit,
    onSave: (RouterDevice) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var ip by remember { mutableStateOf(initial?.ip ?: defaultIp) }
    var username by remember { mutableStateOf(initial?.username ?: "admin") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                if (initial == null) "Add Router Profile" else "Edit Router Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Router Name (e.g. TP-Link Living Room)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP Address (e.g. 192.168.0.1)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Admin Username") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Admin Password") },
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Password"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && ip.isNotBlank()) {
                        onSave(
                            RouterDevice(
                                id = initial?.id ?: System.currentTimeMillis().toString(),
                                name = name.trim(),
                                ip = ip.trim(),
                                username = username.trim(),
                                password = password
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}

@Composable
fun ChangeSubnetDialog(
    initialSubnet: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var subnetText by remember { mutableStateOf(initialSubnet) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                "Network Subnet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter the first 3 octets of your network subnet to scan (e.g. 192.168.0):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = subnetText,
                    onValueChange = { subnetText = it.trim() },
                    label = { Text("Subnet Prefix") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("192.168.0", "192.168.1", "10.0.0").forEach { preset ->
                        SuggestionChip(
                            onClick = { subnetText = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (subnetText.isNotBlank()) {
                        onConfirm(subnetText)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Scan", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}

// -------------------------------------------------------------
// STORAGE & UTILITIES
// -------------------------------------------------------------

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun getSecurePrefs(context: Context) = EncryptedSharedPreferences.create(
    context,
    "router_hub_secure_prefs",
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

private fun saveRouters(context: Context, list: List<RouterDevice>) {
    val array = JSONArray()
    list.forEach { r ->
        array.put(JSONObject().apply {
            put("id", r.id)
            put("name", r.name)
            put("ip", r.ip)
            put("username", r.username)
            put("password", r.password)
        })
    }
    getSecurePrefs(context).edit().putString("routers_json", array.toString()).apply()
}

private fun loadRouters(context: Context): List<RouterDevice> {
    val raw = getSecurePrefs(context).getString("routers_json", null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            RouterDevice(
                id = obj.getString("id"),
                name = obj.getString("name"),
                ip = obj.getString("ip"),
                username = obj.optString("username", "admin"),
                password = obj.optString("password", "")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

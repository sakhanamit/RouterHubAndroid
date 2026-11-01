package com.routerhub.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.routerhub.model.RouterDevice

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouterWebViewScreen(
    router: RouterDevice,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf(router.name) }
    var currentUrl by remember { mutableStateOf(router.ip) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var isDesktopMode by remember { mutableStateOf(false) }

    var jsDialogMessage by remember { mutableStateOf<String?>(null) }
    var jsResultCallback by remember { mutableStateOf<JsResult?>(null) }

    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (pageTitle.isNotBlank()) pageTitle else router.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close / Back to App")
                    }
                },
                actions = {
                    // Desktop site toggle (fixes TP-Link and legacy admin pages)
                    IconButton(onClick = {
                        isDesktopMode = !isDesktopMode
                        webViewRef?.let { wv ->
                            wv.settings.userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else null
                            wv.reload()
                        }
                        Toast.makeText(
                            context,
                            if (isDesktopMode) "Desktop Mode Enabled" else "Mobile Mode Enabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                            contentDescription = "Toggle Desktop Mode",
                            tint = if (isDesktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Reload button
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }

                    // Open in Chrome / External browser
                    IconButton(onClick = {
                        try {
                            val target = if (currentUrl.startsWith("http")) currentUrl else "http://$currentUrl"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in External Browser")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Loading Progress Indicator
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    progress = { loadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Quick Credential Helper Bar
            if (router.username.isNotBlank() || router.password.isNotBlank()) {
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Credentials:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.weight(1f))

                        if (router.username.isNotBlank()) {
                            AssistChip(
                                onClick = { copyToClipboard(context, "Username", router.username) },
                                label = { Text("Copy User") }
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        if (router.password.isNotBlank()) {
                            AssistChip(
                                onClick = { copyToClipboard(context, "Password", router.password) },
                                label = { Text("Copy Pass") }
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        FilledTonalButton(
                            onClick = { webViewRef?.let { injectAutoLogin(it, router) } },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Fill", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Native Android WebView with full Router & TP-Link support
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            javaScriptCanOpenWindowsAutomatically = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }

                        val webViewInstance = this
                        // Enable cookies for router sessions (LuCI, TP-Link, etc.)
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(webViewInstance, true)
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                                isLoading = newProgress < 100
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    pageTitle = title
                                }
                            }

                            override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                jsDialogMessage = message ?: "Router alert"
                                jsResultCallback = result
                                return true
                            }

                            override fun onJsConfirm(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                jsDialogMessage = message ?: "Router confirmation"
                                jsResultCallback = result
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            // Proceed on self-signed HTTPS certificates typical on local routers
                            @SuppressLint("WebViewClientOnReceivedSslError")
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                handler?.proceed()
                            }

                            // Handle HTTP Basic / Digest Authentication
                            override fun onReceivedHttpAuthRequest(
                                view: WebView?,
                                handler: HttpAuthHandler?,
                                host: String?,
                                realm: String?
                            ) {
                                if (router.username.isNotBlank() && router.password.isNotBlank()) {
                                    handler?.proceed(router.username, router.password)
                                } else {
                                    handler?.cancel()
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                currentUrl = url
                                return false // Allow WebView to handle redirects internally
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url != null) currentUrl = url
                                injectAutoLogin(this@apply, router)
                            }
                        }

                        val targetUrl = if (router.ip.startsWith("http")) router.ip else "http://${router.ip}"
                        loadUrl(targetUrl)
                        webViewRef = this
                    }
                }
            )
        }
    }

    // Native JS Dialog for alerts/confirms
    if (jsDialogMessage != null) {
        AlertDialog(
            onDismissRequest = {
                jsResultCallback?.cancel()
                jsDialogMessage = null
                jsResultCallback = null
            },
            title = { Text("Router Message") },
            text = { Text(jsDialogMessage!!) },
            confirmButton = {
                Button(onClick = {
                    jsResultCallback?.confirm()
                    jsDialogMessage = null
                    jsResultCallback = null
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    jsResultCallback?.cancel()
                    jsDialogMessage = null
                    jsResultCallback = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Intelligent Auto-Fill for routers:
 * Supports standard HTTP forms, LuCI (OpenWrt), and TP-Link Archer/Deco web panels.
 */
private fun injectAutoLogin(view: WebView, router: RouterDevice) {
    if (router.username.isBlank() && router.password.isBlank()) return

    val js = """
        (function() {
            var user = '${router.username}';
            var pass = '${router.password}';
            
            // 1. LuCI specific fields
            var luciUser = document.getElementById('luci_username') || document.querySelector('input[name="luci_username"]');
            var luciPass = document.getElementById('luci_password') || document.querySelector('input[name="luci_password"]');
            if (luciUser && user) { luciUser.value = user; luciUser.dispatchEvent(new Event('input', {bubbles:true})); }
            if (luciPass && pass) { luciPass.value = pass; luciPass.dispatchEvent(new Event('input', {bubbles:true})); }

            // 2. TP-Link specific fields (Archer, Deco, C6, etc.)
            var tpPass = document.getElementById('pc-login-password') || 
                         document.getElementById('login-password') || 
                         document.getElementById('user_password') ||
                         document.querySelector('.password-text');
            if (tpPass && pass) { 
                tpPass.value = pass; 
                tpPass.dispatchEvent(new Event('input', {bubbles:true})); 
                tpPass.dispatchEvent(new Event('change', {bubbles:true})); 
            }

            // 3. Generic fallback
            var inputs = document.getElementsByTagName('input');
            for (var i = 0; i < inputs.length; i++) {
                var el = inputs[i];
                var type = (el.type || '').toLowerCase();
                var name = (el.name || el.id || '').toLowerCase();
                
                if (type === 'text' && (name.includes('user') || name.includes('admin') || name.includes('login'))) {
                    if (user && !el.value) {
                        el.value = user;
                        el.dispatchEvent(new Event('input', {bubbles:true}));
                    }
                }
                if (type === 'password') {
                    if (pass && !el.value) {
                        el.value = pass;
                        el.dispatchEvent(new Event('input', {bubbles:true}));
                        el.dispatchEvent(new Event('change', {bubbles:true}));
                    }
                }
            }
        })();
    """.trimIndent()

    view.evaluateJavascript(js, null)
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

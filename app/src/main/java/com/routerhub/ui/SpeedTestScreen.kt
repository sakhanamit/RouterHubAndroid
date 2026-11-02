package com.routerhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerhub.network.SpeedTestPhase
import com.routerhub.network.SpeedTestResult
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    result: SpeedTestResult,
    isRunning: Boolean,
    onStartTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Determine current speed to show on gauge
    val displaySpeed = when (result.phase) {
        SpeedTestPhase.DOWNLOAD -> result.currentDownloadMbps
        SpeedTestPhase.UPLOAD -> result.currentUploadMbps
        SpeedTestPhase.COMPLETE -> result.peakDownloadMbps
        else -> 0f
    }

    val animatedSpeed by animateFloatAsState(
        targetValue = if (displaySpeed.isFinite() && displaySpeed >= 0f) displaySpeed else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "animatedSpeed"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = result.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250),
        label = "animatedProgress"
    )

    // Dynamic adaptive gauge maximum based on connection speed
    val highestObserved = max(
        max(result.peakDownloadMbps, result.peakUploadMbps),
        max(result.currentDownloadMbps, result.currentUploadMbps)
    )
    val maxGaugeSpeed = when {
        highestObserved > 500f -> 1000f
        highestObserved > 250f -> 500f
        highestObserved > 100f -> 250f
        highestObserved > 50f -> 100f
        else -> 100f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // -------------------------------------------------------------
        // Top Phase & Server Info Banner
        // -------------------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(2.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val badgeColor = when (result.phase) {
                        SpeedTestPhase.IDLE -> MaterialTheme.colorScheme.secondary
                        SpeedTestPhase.PING -> Color(0xFFF59E0B)
                        SpeedTestPhase.DOWNLOAD -> Color(0xFF10B981)
                        SpeedTestPhase.UPLOAD -> Color(0xFF06B6D4)
                        SpeedTestPhase.COMPLETE -> MaterialTheme.colorScheme.primary
                        SpeedTestPhase.ERROR -> MaterialTheme.colorScheme.error
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = when (result.phase) {
                                SpeedTestPhase.IDLE -> "READY"
                                SpeedTestPhase.PING -> "STEP 1/3 • PING LATENCY"
                                SpeedTestPhase.DOWNLOAD -> "STEP 2/3 • DOWNLOADING"
                                SpeedTestPhase.UPLOAD -> "STEP 3/3 • UPLOADING"
                                SpeedTestPhase.COMPLETE -> "TEST COMPLETED"
                                SpeedTestPhase.ERROR -> "TEST ERROR"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = result.statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = result.serverName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isRunning) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = when (result.phase) {
                            SpeedTestPhase.DOWNLOAD -> Color(0xFF10B981)
                            SpeedTestPhase.UPLOAD -> Color(0xFF06B6D4)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // Circular Speedometer Gauge with Adaptive Scale & Sparkline
        // -------------------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(3.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ModernSpeedometerGauge(
                        speedMbps = animatedSpeed,
                        maxSpeedMbps = maxGaugeSpeed,
                        phase = result.phase,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Center Digital Readout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 22.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            when (result.phase) {
                                SpeedTestPhase.DOWNLOAD -> {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                SpeedTestPhase.UPLOAD -> {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = Color(0xFF06B6D4),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                else -> {}
                            }

                            Text(
                                text = when (result.phase) {
                                    SpeedTestPhase.PING -> "PINGING"
                                    SpeedTestPhase.DOWNLOAD -> "DOWNLOAD"
                                    SpeedTestPhase.UPLOAD -> "UPLOAD"
                                    SpeedTestPhase.COMPLETE -> "DOWNLOAD PEAK"
                                    else -> "SPEED"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (result.phase) {
                                    SpeedTestPhase.DOWNLOAD -> Color(0xFF10B981)
                                    SpeedTestPhase.UPLOAD -> Color(0xFF06B6D4)
                                    SpeedTestPhase.COMPLETE -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Text(
                            text = String.format(Locale.US, "%.1f", animatedSpeed),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Mbps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Scale 0 - ${maxGaugeSpeed.toInt()} Mbps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Live Speed History Wave / Sparkline
                if (result.speedHistory.size > 2) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "LIVE THROUGHPUT STREAM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    SpeedSparkline(
                        history = result.speedHistory,
                        phase = result.phase,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 12.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // Primary Metrics Cards: Download & Upload
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Download Metric Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = "Download",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Download",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = String.format(Locale.US, "%.1f", if (result.peakDownloadMbps.isFinite()) result.peakDownloadMbps else 0f),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Mbps (Peak)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(6.dp))

                    if (result.downloadBytes > 0) {
                        val mbDownloaded = result.downloadBytes / (1024f * 1024f)
                        Text(
                            text = String.format(Locale.US, "Transferred: %.1f MB", mbDownloaded),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Upload Metric Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF06B6D4).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Upload",
                                tint = Color(0xFF06B6D4),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Upload",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = String.format(Locale.US, "%.1f", if (result.peakUploadMbps.isFinite()) result.peakUploadMbps else 0f),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Mbps (Peak)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(6.dp))

                    if (result.uploadBytes > 0) {
                        val mbUploaded = result.uploadBytes / (1024f * 1024f)
                        Text(
                            text = String.format(Locale.US, "Transferred: %.1f MB", mbUploaded),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Latency Metrics Cards: Ping & Jitter
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ping Latency Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Ping Latency",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${result.pingMs} ms",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        val pingGrade = when {
                            result.pingMs in 1..25 -> "Ultra Low"
                            result.pingMs in 26..50 -> "Good"
                            result.pingMs > 50 -> "Fair"
                            else -> "Testing"
                        }
                        Text(
                            pingGrade,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (result.pingMs in 1..35) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Jitter Variance Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Jitter Variance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${result.jitterMs} ms",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        val jitterGrade = when {
                            result.jitterMs in 0..5 -> "Stable"
                            result.jitterMs in 6..15 -> "Moderate"
                            result.jitterMs > 15 -> "High Jitter"
                            else -> "Testing"
                        }
                        Text(
                            jitterGrade,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (result.jitterMs in 0..6) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Connection Quality & Capabilities (Shown after test completion)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = result.phase == SpeedTestPhase.COMPLETE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(2.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Network Performance Capabilities",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    CapabilityItem(
                        title = "4K / 8K Video Streaming",
                        subtitle = "Netflix, YouTube, Prime Video UHD",
                        isCapable = result.peakDownloadMbps >= 25f
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    CapabilityItem(
                        title = "Online Gaming & Low Latency",
                        subtitle = "Multiplayer games, fast reaction time",
                        isCapable = result.pingMs < 60 && result.jitterMs < 20
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    CapabilityItem(
                        title = "HD Video Conferencing",
                        subtitle = "Zoom, Teams, Google Meet HD calls",
                        isCapable = result.peakDownloadMbps >= 10f && result.peakUploadMbps >= 5f
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    CapabilityItem(
                        title = "Fast Cloud Backups & Large Uploads",
                        subtitle = "Drive, Photos, large media syncing",
                        isCapable = result.peakUploadMbps >= 15f
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // -------------------------------------------------------------
        // Start / Restart Speed Test Button
        // -------------------------------------------------------------
        Button(
            onClick = onStartTest,
            enabled = !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Testing Connection...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (result.phase == SpeedTestPhase.COMPLETE) "START TEST AGAIN" else "START ACCURATE SPEED TEST",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun CapabilityItem(
    title: String,
    subtitle: String,
    isCapable: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isCapable) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (isCapable) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isCapable) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = if (isCapable) "OPTIMAL" else "FAIR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isCapable) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun ModernSpeedometerGauge(
    speedMbps: Float,
    maxSpeedMbps: Float = 100f,
    phase: SpeedTestPhase,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val downloadColor = Color(0xFF10B981)
    val uploadColor = Color(0xFF06B6D4)
    val completeColor = Color(0xFF10B981)

    val activeColor = when (phase) {
        SpeedTestPhase.DOWNLOAD -> downloadColor
        SpeedTestPhase.UPLOAD -> uploadColor
        SpeedTestPhase.COMPLETE -> completeColor
        else -> primaryColor
    }

    Canvas(modifier = modifier) {
        val strokeWidth = 16.dp.toPx()
        val radius = (size.minDimension - strokeWidth * 2) / 2
        val center = Offset(size.width / 2, size.height / 2)

        // 240 degree gauge arc from 150° (bottom-left) to 390° (bottom-right)
        val startAngle = 150f
        val sweepAngle = 240f

        // Draw Background Gauge Track
        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Draw Graduation Tick Marks
        val tickCount = 10
        for (i in 0..tickCount) {
            val fraction = i.toFloat() / tickCount.toFloat()
            val angleDeg = startAngle + (sweepAngle * fraction)
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val innerTickRadius = radius - (strokeWidth / 2) - 6.dp.toPx()
            val outerTickRadius = innerTickRadius - (if (i % 2 == 0) 10.dp.toPx() else 6.dp.toPx())

            val startX = center.x + innerTickRadius * cos(angleRad).toFloat()
            val startY = center.y + innerTickRadius * sin(angleRad).toFloat()
            val endX = center.x + outerTickRadius * cos(angleRad).toFloat()
            val endY = center.y + outerTickRadius * sin(angleRad).toFloat()

            drawLine(
                color = if (fraction <= (speedMbps / maxSpeedMbps)) activeColor.copy(alpha = 0.8f) else trackColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = if (i % 2 == 0) 2.5.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Draw Active Speed Progress Arc
        val clampedSpeed = speedMbps.coerceIn(0f, maxSpeedMbps)
        val speedRatio = clampedSpeed / maxSpeedMbps
        val activeSweep = (sweepAngle * speedRatio).coerceIn(0f, sweepAngle)

        if (activeSweep > 0.5f) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(primaryColor, uploadColor, downloadColor, activeColor)
                ),
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Draw Needle / Glowing Pointer Indicator
        val needleAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
        val needleX = center.x + radius * cos(needleAngleRad).toFloat()
        val needleY = center.y + radius * sin(needleAngleRad).toFloat()

        // Outer glow
        drawCircle(
            color = activeColor.copy(alpha = 0.35f),
            radius = 12.dp.toPx(),
            center = Offset(needleX, needleY)
        )
        // White core
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(needleX, needleY)
        )
        // Active color inner dot
        drawCircle(
            color = activeColor,
            radius = 4.dp.toPx(),
            center = Offset(needleX, needleY)
        )
    }
}

@Composable
fun SpeedSparkline(
    history: List<Float>,
    phase: SpeedTestPhase,
    modifier: Modifier = Modifier
) {
    val lineColor = when (phase) {
        SpeedTestPhase.DOWNLOAD -> Color(0xFF10B981)
        SpeedTestPhase.UPLOAD -> Color(0xFF06B6D4)
        else -> MaterialTheme.colorScheme.primary
    }

    Canvas(modifier = modifier) {
        if (history.size < 2) return@Canvas

        val maxVal = max(1f, history.maxOrNull() ?: 1f)
        val width = size.width
        val height = size.height
        val stepX = width / (history.size - 1)

        val path = Path()
        val fillPath = Path()

        fillPath.moveTo(0f, height)

        history.forEachIndexed { index, value ->
            val x = index * stepX
            val normalizedY = 1f - (value / maxVal).coerceIn(0f, 1f)
            val y = normalizedY * (height - 8.dp.toPx()) + 4.dp.toPx()

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(width, height)
        fillPath.close()

        // Draw gradient under the curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        // Draw the throughput stroke
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

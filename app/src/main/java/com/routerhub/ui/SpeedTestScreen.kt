package com.routerhub.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routerhub.network.SpeedTestManager
import com.routerhub.network.SpeedTestPhase
import com.routerhub.network.SpeedTestResult
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    result: SpeedTestResult,
    isRunning: Boolean,
    onStartTest: () -> Unit
) {
    val scrollState = rememberScrollState()

    val displaySpeed = when (result.phase) {
        SpeedTestPhase.DOWNLOAD -> result.currentDownloadMbps
        SpeedTestPhase.UPLOAD -> result.currentUploadMbps
        SpeedTestPhase.COMPLETE -> result.peakDownloadMbps
        else -> 0f
    }

    val animatedSpeed by animateFloatAsState(
        targetValue = displaySpeed,
        animationSpec = tween(durationMillis = 300),
        label = "animatedSpeed"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Top Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Internet Speed Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Circular Speedometer Gauge
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                SpeedometerGauge(
                    speedMbps = animatedSpeed,
                    maxSpeedMbps = 100f,
                    phase = result.phase,
                    modifier = Modifier.size(260.dp)
                )

                // Center Numerical Speed Counter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text(
                        text = when (result.phase) {
                            SpeedTestPhase.PING -> "PING"
                            SpeedTestPhase.DOWNLOAD -> "DOWN"
                            SpeedTestPhase.UPLOAD -> "UP"
                            SpeedTestPhase.COMPLETE -> "RESULT"
                            else -> "READY"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", if (animatedSpeed.isFinite()) animatedSpeed else 0f),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Mbps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Metrics Grid (Download, Upload, Ping, Jitter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Download Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Download",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Download", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", if (result.peakDownloadMbps.isFinite()) result.peakDownloadMbps else 0f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("Mbps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Upload Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Upload",
                            tint = Color(0xFF06B6D4),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Upload", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", if (result.peakUploadMbps.isFinite()) result.peakUploadMbps else 0f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("Mbps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Secondary Metrics Row (Ping & Jitter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Ping Latency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${result.pingMs} ms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Jitter Variance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${result.jitterMs} ms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Start / Restart Speed Test Button
        Button(
            onClick = onStartTest,
            enabled = !isRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(12.dp))
                Text("Testing Speed...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (result.phase == SpeedTestPhase.COMPLETE) "START TEST AGAIN" else "START SPEED TEST",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SpeedometerGauge(
    speedMbps: Float,
    maxSpeedMbps: Float = 100f,
    phase: SpeedTestPhase,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = Color(0xFF06B6D4)
    val completeColor = Color(0xFF10B981)

    val activeColor = when (phase) {
        SpeedTestPhase.DOWNLOAD -> primaryColor
        SpeedTestPhase.UPLOAD -> secondaryColor
        SpeedTestPhase.COMPLETE -> completeColor
        else -> primaryColor
    }

    Canvas(modifier = modifier) {
        val strokeWidth = 18.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        // Arc start and sweep angle (240 degree gauge arc from 150° to 390°)
        val startAngle = 150f
        val sweepAngle = 240f

        // Draw Track Background Arc
        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Draw Active Speed Progress Arc
        val clampedSpeed = speedMbps.coerceIn(0f, maxSpeedMbps)
        val speedRatio = clampedSpeed / maxSpeedMbps
        val activeSweep = sweepAngle * speedRatio

        if (activeSweep > 0.5f) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(primaryColor, secondaryColor, completeColor)
                ),
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Draw Needle / Pointer Circle
        val needleAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
        val needleX = center.x + (radius - 4.dp.toPx()) * cos(needleAngleRad).toFloat()
        val needleY = center.y + (radius - 4.dp.toPx()) * sin(needleAngleRad).toFloat()

        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(needleX, needleY)
        )
        drawCircle(
            color = activeColor,
            radius = 4.dp.toPx(),
            center = Offset(needleX, needleY)
        )
    }
}

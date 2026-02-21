package com.otavio.opticcore.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SensorOccupied
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otavio.opticcore.data.model.CameraDeviceReport
import com.otavio.opticcore.data.model.CameraLensInfo
import com.otavio.opticcore.data.model.LensFacing
import com.otavio.opticcore.data.model.LensType
import com.otavio.opticcore.ui.theme.AccentBlue
import com.otavio.opticcore.ui.theme.AccentBlueDark
import com.otavio.opticcore.ui.theme.DarkCard
import com.otavio.opticcore.ui.theme.DarkCardElevated
import com.otavio.opticcore.ui.theme.StatusAmber
import com.otavio.opticcore.ui.theme.StatusCyan
import com.otavio.opticcore.ui.theme.StatusGreen
import com.otavio.opticcore.ui.theme.StatusRed
import com.otavio.opticcore.ui.theme.TextSecondary
import com.otavio.opticcore.ui.theme.TextTertiary
import com.otavio.opticcore.ui.viewmodel.ScanState
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────
// Tela principal que exibe as capacidades de câmera detectadas
// ─────────────────────────────────────────────────────────────

@Composable
fun CameraInfoScreen(
    scanState: ScanState,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (scanState) {
            is ScanState.Idle -> ScanIdleContent()
            is ScanState.Scanning -> ScanLoadingContent()
            is ScanState.Success -> ScanSuccessContent(
                report = scanState.report,
                onRescan = onRescan
            )
            is ScanState.Error -> ScanErrorContent(
                message = scanState.message,
                onRetry = onRescan
            )
        }
    }
}

// ─── Loading ────────────────────────────────────────────────

@Composable
private fun ScanIdleContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = TextTertiary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Aguardando permissão de câmera...",
                color = TextSecondary,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun ScanLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = AccentBlue,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Escaneando hardware...",
                color = TextSecondary,
                fontSize = 16.sp
            )
        }
    }
}

// ─── Error ──────────────────────────────────────────────────

@Composable
private fun ScanErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⚠️ Erro no scan",
                color = StatusRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Tentar novamente",
                    tint = AccentBlue,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// ─── Success ────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanSuccessContent(
    report: CameraDeviceReport,
    onRescan: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ──
        item {
            Spacer(modifier = Modifier.height(16.dp))
            DeviceHeader(report = report, onRescan = onRescan)
        }

        // ── Resumo rápido ──
        item {
            QuickSummaryRow(report = report)
        }

        // ── Cards de câmera ──
        val backLenses = report.lenses.filter { it.lensFacing == LensFacing.BACK }
        val frontLenses = report.lenses.filter { it.lensFacing == LensFacing.FRONT }

        if (backLenses.isNotEmpty()) {
            item {
                Text(
                    text = "CÂMERAS TRASEIRAS",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
            itemsIndexed(backLenses) { index, lens ->
                AnimatedLensCard(lens = lens, delayMs = index * 100)
            }
        }

        if (frontLenses.isNotEmpty()) {
            item {
                Text(
                    text = "CÂMERAS FRONTAIS",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
            itemsIndexed(frontLenses) { index, lens ->
                AnimatedLensCard(lens = lens, delayMs = (backLenses.size + index) * 100)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ─── Header do dispositivo ──────────────────────────────────

@Composable
private fun DeviceHeader(report: CameraDeviceReport, onRescan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "OpticCore",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AccentBlue
            )
            Text(
                text = report.deviceModel,
                fontSize = 14.sp,
                color = TextSecondary
            )
            Text(
                text = "Android ${report.androidVersion} · SDK ${report.sdkVersion}",
                fontSize = 12.sp,
                color = TextTertiary
            )
        }

        IconButton(
            onClick = onRescan,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DarkCardElevated)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Re-scan",
                tint = AccentBlue,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ─── Resumo rápido ──────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSummaryRow(report: CameraDeviceReport) {
    val backCount = report.lenses.count { it.lensFacing == LensFacing.BACK }
    val frontCount = report.lenses.count { it.lensFacing == LensFacing.FRONT }
    val rawCount = report.lenses.count { it.rawSupported }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryChip(label = "${report.totalCameras} câmeras", color = AccentBlue)
        SummaryChip(label = "$backCount traseiras", color = StatusCyan)
        SummaryChip(label = "$frontCount frontais", color = StatusAmber)
        if (rawCount > 0) {
            SummaryChip(label = "RAW ✓", color = StatusGreen)
        }
    }
}

@Composable
private fun SummaryChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Card de lente individual ───────────────────────────────

@Composable
private fun AnimatedLensCard(lens: CameraLensInfo, delayMs: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
    ) {
        LensCard(lens = lens)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LensCard(lens: CameraLensInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Cabeçalho da lente ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(AccentBlueDark, AccentBlue)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = lensTypeIcon(lens.lensType),
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lens.lensType.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ID: ${lens.cameraId} · ${lens.lensType.symbol} · ${lens.hardwareLevel}",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }
                // megapixels badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "%.0f MP".format(lens.megapixels),
                        color = AccentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Grid de specs ──
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Focal length
                val focalText = lens.focalLengths.joinToString(", ") { "%.2f mm".format(it) }
                SpecBadge(label = "Focal", value = focalText)

                // Abertura
                val apertureText = lens.apertures.joinToString(", ") { "f/%.1f".format(it) }
                SpecBadge(label = "Abertura", value = apertureText)

                // OIS
                SpecBadge(
                    label = "OIS",
                    value = if (lens.opticalStabilization) "Sim" else "Não",
                    valueColor = if (lens.opticalStabilization) StatusGreen else StatusRed
                )

                // Zoom
                SpecBadge(label = "Zoom Max", value = "%.1fx".format(lens.maxZoom))

                // RAW
                SpecBadge(
                    label = "RAW",
                    value = if (lens.rawSupported) "Sim" else "Não",
                    valueColor = if (lens.rawSupported) StatusGreen else StatusRed
                )

                // YUV
                SpecBadge(
                    label = "YUV",
                    value = if (lens.yuvSupported) "Sim" else "Não",
                    valueColor = if (lens.yuvSupported) StatusGreen else StatusRed
                )
            }

            // ── ISO & Exposure ──
            if (lens.isoRange != null || lens.exposureTimeRange != null) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lens.isoRange?.let {
                        SpecBadge(label = "ISO", value = "${it.first} – ${it.last}")
                    }
                    lens.exposureTimeRange?.let {
                        val minMs = it.first / 1_000_000.0
                        val maxSec = it.last / 1_000_000_000.0
                        SpecBadge(label = "Exposição", value = "%.3fms – %.1fs".format(minMs, maxSec))
                    }
                }
            }

            // ── Sensor ──
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Sensor: ${lens.sensorSize}",
                fontSize = 11.sp,
                color = TextTertiary
            )

            // ── Top resoluções ──
            val topRes = lens.supportedResolutions
                .distinctBy { "${it.width}x${it.height}" }
                .sortedByDescending { it.width.toLong() * it.height.toLong() }
                .take(3)
            if (topRes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Top resoluções: " + topRes.joinToString(" · ") { "${it.width}×${it.height}" },
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }

            // ── Physical IDs ──
            if (lens.physicalCameraIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Câmeras físicas: ${lens.physicalCameraIds.joinToString(", ")}",
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }
        }
    }
}

// ─── Spec Badge ─────────────────────────────────────────────

@Composable
private fun SpecBadge(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCardElevated)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextTertiary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Helpers ────────────────────────────────────────────────

private fun lensTypeIcon(type: LensType): ImageVector {
    return when (type) {
        LensType.ULTRAWIDE -> Icons.Filled.Landscape
        LensType.WIDE -> Icons.Filled.PhotoCamera
        LensType.TELEPHOTO -> Icons.Filled.ZoomIn
        LensType.MACRO -> Icons.Filled.CenterFocusWeak
        LensType.FRONT -> Icons.Filled.CameraFront
        LensType.UNKNOWN -> Icons.Filled.Camera
    }
}

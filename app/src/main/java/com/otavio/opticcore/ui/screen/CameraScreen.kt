package com.otavio.opticcore.ui.screen

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.otavio.opticcore.data.model.CaptureState
import com.otavio.opticcore.data.model.PreviewState
import com.otavio.opticcore.data.model.ProcessingState
import com.otavio.opticcore.data.processing.ImageProcessor
import com.otavio.opticcore.ui.theme.AccentBlue
import com.otavio.opticcore.ui.theme.AccentBlueDark
import com.otavio.opticcore.ui.theme.DarkCard
import com.otavio.opticcore.ui.theme.DarkCardElevated
import com.otavio.opticcore.ui.theme.DarkSurface
import com.otavio.opticcore.ui.theme.StatusGreen
import com.otavio.opticcore.ui.theme.StatusRed
import com.otavio.opticcore.ui.theme.TextPrimary
import com.otavio.opticcore.ui.theme.TextSecondary
import com.otavio.opticcore.ui.theme.TextTertiary
import com.otavio.opticcore.ui.viewmodel.CameraViewModel

// ═══════════════════════════════════════════════════════════
//  CameraScreen — Viewfinder + Controles + Settings Panel
// ═══════════════════════════════════════════════════════════

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    previewState: PreviewState,
    captureState: CaptureState,
    processingState: ProcessingState,
    currentLensIndex: Int,
    availableLenses: List<CameraViewModel.LensInfo>,
    processingSettings: ImageProcessor.Settings,
    showSettings: Boolean,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }

    // Lifecycle management
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    surfaceHolder?.surface?.let { surface ->
                        if (surface.isValid) viewModel.startPreview(surface)
                    }
                }
                Lifecycle.Event.ON_DESTROY -> viewModel.sessionManager.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier.fillMaxSize().background(DarkSurface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ──
            TopBar(
                onInfoClick = onInfoClick,
                onSettingsClick = { viewModel.toggleSettings() },
                settingsActive = showSettings,
                processingEnabled = processingSettings.enabled
            )

            // ── Viewfinder ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    surfaceHolder = holder
                                    viewModel.startPreview(holder.surface)
                                }
                                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, he: Int) {}
                                override fun surfaceDestroyed(h: SurfaceHolder) { surfaceHolder = null }
                            })
                        }
                    },
                    modifier = Modifier
                        .aspectRatio(3f / 4f) // Proporção 3:4 (retrato do sensor 4:3)
                        .fillMaxSize()
                )

                // Preview loading
                if (previewState is PreviewState.Starting) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                    }
                }

                // Capture flash
                if (captureState is CaptureState.Capturing) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.25f)))
                }

                // Preview error
                if (previewState is PreviewState.Error) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚠️ ${previewState.message}", color = StatusRed, fontSize = 14.sp)
                    }
                }

                // ── Processing overlay ──
                if (processingState is ProcessingState.Processing) {
                    ProcessingOverlay(progress = processingState.progress)
                }

                // Processing done flash
                if (processingState is ProcessingState.Done) {
                    Box(
                        Modifier.fillMaxSize().background(StatusGreen.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Check, "Salvo",
                            tint = StatusGreen,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // ── Settings panel (expandable) ──
            if (showSettings) {
                SettingsPanel(
                    settings = processingSettings,
                    onSettingsChange = { viewModel.updateSettings(it) },
                    onReset = { viewModel.resetSettings() }
                )
            }

            // ── Controls ──
            ControlsArea(
                viewModel = viewModel,
                captureState = captureState,
                processingState = processingState,
                currentLensIndex = currentLensIndex,
                availableLenses = availableLenses,
                surfaceHolder = surfaceHolder
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  Processing Overlay
// ═══════════════════════════════════════════════════════════

@Composable
private fun ProcessingOverlay(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.7f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🧠 Processando...",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(16.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentBlue,
                trackColor = DarkCard,
            )

            Spacer(Modifier.height(8.dp))

            val stage = when {
                progress < 0.10f -> "Decodificando + Orientação..."
                progress < 0.30f -> "S-Curve (Tone Map)"
                progress < 0.45f -> "Color Science"
                progress < 0.55f -> "Clarity (Local Contrast)"
                progress < 0.68f -> "Sharpening"
                progress < 0.80f -> "Vignette"
                progress < 0.95f -> "Codificando JPEG"
                else -> "Salvando..."
            }

            Text(
                text = stage,
                color = TextTertiary,
                fontSize = 12.sp
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                color = AccentBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  Settings Panel
// ═══════════════════════════════════════════════════════════

@Composable
private fun SettingsPanel(
    settings: ImageProcessor.Settings,
    onSettingsChange: ((ImageProcessor.Settings) -> ImageProcessor.Settings) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ajustes de Imagem", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Toggle on/off
                Text(
                    text = if (settings.enabled) "ON" else "OFF",
                    color = if (settings.enabled) StatusGreen else TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (settings.enabled) StatusGreen.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            onSettingsChange { it.copy(enabled = !it.enabled) }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )

                Spacer(Modifier.width(8.dp))

                // Reset
                IconButton(onClick = onReset, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Refresh, "Reset", tint = TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (settings.enabled) {
            Spacer(Modifier.height(8.dp))

            // Shadows
            SettingSlider(
                label = "Sombras",
                value = settings.shadows,
                range = -0.5f..0.5f,
                format = { "%.0f".format(it * 100) },
                onValueChange = { v -> onSettingsChange { it.copy(shadows = v) } }
            )

            // Highlights
            SettingSlider(
                label = "Realces",
                value = settings.highlights,
                range = -0.5f..0.5f,
                format = { "%.0f".format(it * 100) },
                onValueChange = { v -> onSettingsChange { it.copy(highlights = v) } }
            )

            // Temperature
            SettingSlider(
                label = "Temperatura",
                value = settings.temperature,
                range = -50f..50f,
                format = { "%.0f".format(it) },
                onValueChange = { v -> onSettingsChange { it.copy(temperature = v) } }
            )

            // Saturation
            SettingSlider(
                label = "Saturação",
                value = settings.saturation,
                range = 0.5f..1.8f,
                format = { "%.0f%%".format(it * 100) },
                onValueChange = { v -> onSettingsChange { it.copy(saturation = v) } }
            )

            // Contrast
            SettingSlider(
                label = "Contraste",
                value = settings.contrast,
                range = 0.7f..1.4f,
                format = { "%.0f%%".format(it * 100) },
                onValueChange = { v -> onSettingsChange { it.copy(contrast = v) } }
            )

            // Tint
            SettingSlider(
                label = "Tint",
                value = settings.tint,
                range = -30f..30f,
                format = { "%.0f".format(it) },
                onValueChange = { v -> onSettingsChange { it.copy(tint = v) } }
            )

            // Vibrance
            SettingSlider(
                label = "Vibrance",
                value = settings.vibrance,
                range = 0.8f..1.5f,
                format = { "%.0f%%".format(it * 100) },
                onValueChange = { v -> onSettingsChange { it.copy(vibrance = v) } }
            )

            // Clarity
            SettingSlider(
                label = "Nitidez+",
                value = settings.clarity,
                range = 0f..0.5f,
                format = { "%.0f%%".format(it * 200) },
                onValueChange = { v -> onSettingsChange { it.copy(clarity = v) } }
            )

            // Sharpening
            SettingSlider(
                label = "Sharpening",
                value = settings.sharpenAmount,
                range = 0f..1f,
                format = { "%.0f%%".format(it * 100) },
                onValueChange = { v -> onSettingsChange { it.copy(sharpenAmount = v) } }
            )

            // Vignette
            SettingSlider(
                label = "Vinheta",
                value = settings.vignetteStrength,
                range = 0f..0.4f,
                format = { "%.0f%%".format(it * 250) },
                onValueChange = { v -> onSettingsChange { it.copy(vignetteStrength = v) } }
            )
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(80.dp)
        )

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = AccentBlue,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = DarkCardElevated
            )
        )

        Text(
            text = format(value),
            color = TextTertiary,
            fontSize = 11.sp,
            modifier = Modifier.width(42.dp),
            maxLines = 1
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  Top Bar
// ═══════════════════════════════════════════════════════════

@Composable
private fun TopBar(
    onInfoClick: () -> Unit,
    onSettingsClick: () -> Unit,
    settingsActive: Boolean,
    processingEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("OpticCore", color = AccentBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (processingEnabled) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(StatusGreen)
                )
            }
        }

        Row {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Filled.Tune, "Ajustes",
                    tint = if (settingsActive) AccentBlue else TextSecondary
                )
            }
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Filled.Info, "Info", tint = TextSecondary)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  Controls Area
// ═══════════════════════════════════════════════════════════

@Composable
private fun ControlsArea(
    viewModel: CameraViewModel,
    captureState: CaptureState,
    processingState: ProcessingState,
    currentLensIndex: Int,
    availableLenses: List<CameraViewModel.LensInfo>,
    surfaceHolder: SurfaceHolder?
) {
    val isProcessing = processingState is ProcessingState.Processing
    val isBusy = captureState is CaptureState.Capturing || isProcessing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Lens selector
        LensSelectorBar(
            lenses = availableLenses,
            selectedIndex = currentLensIndex,
            enabled = !isBusy,
            onLensSelected = { index ->
                surfaceHolder?.surface?.let { surface ->
                    if (surface.isValid) viewModel.switchLens(index, surface)
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        // Shutter + status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LastPhotoThumbnail(captureState = captureState, processingState = processingState)

            ShutterButton(
                isBusy = isBusy,
                onClick = { viewModel.capturePhoto() }
            )

            Spacer(Modifier.size(56.dp))
        }

        Spacer(Modifier.height(4.dp))
    }
}

// ═══════════════════════════════════════════════════════════
//  Lens Selector
// ═══════════════════════════════════════════════════════════

@Composable
private fun LensSelectorBar(
    lenses: List<CameraViewModel.LensInfo>,
    selectedIndex: Int,
    enabled: Boolean,
    onLensSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(DarkCard)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        lenses.forEachIndexed { index, lens ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) AccentBlue.copy(alpha = 0.2f) else Color.Transparent
                    )
                    .clickable(enabled = enabled) { onLensSelected(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lens.label,
                    color = when {
                        isSelected -> AccentBlue
                        !enabled -> TextTertiary.copy(alpha = 0.4f)
                        else -> TextTertiary
                    },
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  Shutter Button
// ═══════════════════════════════════════════════════════════

@Composable
private fun ShutterButton(isBusy: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = tween(100), label = "shutter_scale"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(3.dp, if (isBusy) Color.Gray else Color.White, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isBusy
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (isBusy) Color.Gray.copy(alpha = 0.5f) else Color.White)
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  Last Photo Thumbnail
// ═══════════════════════════════════════════════════════════

@Composable
private fun LastPhotoThumbnail(
    captureState: CaptureState,
    processingState: ProcessingState
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCardElevated)
            .border(1.dp, DarkCard, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            processingState is ProcessingState.Processing -> {
                CircularProgressIndicator(
                    progress = { processingState.progress },
                    modifier = Modifier.size(28.dp),
                    color = AccentBlue,
                    trackColor = DarkCard,
                    strokeWidth = 3.dp
                )
            }
            captureState is CaptureState.Saved -> {
                Icon(Icons.Filled.Check, "Salvo", tint = StatusGreen, modifier = Modifier.size(24.dp))
            }
            captureState is CaptureState.Capturing -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp)
            }
            captureState is CaptureState.Error -> {
                Text("!", color = StatusRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

package com.otavio.opticcore.ui.screen

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.otavio.opticcore.ui.theme.AccentBlue
import com.otavio.opticcore.ui.theme.DarkCard
import com.otavio.opticcore.ui.theme.DarkCardElevated
import com.otavio.opticcore.ui.theme.DarkSurface
import com.otavio.opticcore.ui.theme.StatusGreen
import com.otavio.opticcore.ui.theme.StatusRed
import com.otavio.opticcore.ui.theme.TextSecondary
import com.otavio.opticcore.ui.theme.TextTertiary
import com.otavio.opticcore.ui.viewmodel.CameraViewModel

// ─────────────────────────────────────────────────────────────
// Tela principal da câmera — Viewfinder + Controles
// ─────────────────────────────────────────────────────────────

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    previewState: PreviewState,
    captureState: CaptureState,
    currentLensIndex: Int,
    availableLenses: List<CameraViewModel.LensInfo>,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Surface holder para o preview
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }

    // Lifecycle: liberar recursos quando a Activity for parada
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    surfaceHolder?.surface?.let { surface ->
                        if (surface.isValid) {
                            viewModel.startPreview(surface)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Não libera aqui para permitir re-attach rápido
                }
                Lifecycle.Event.ON_DESTROY -> {
                    viewModel.sessionManager.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Top bar ──
            TopBar(onInfoClick = onInfoClick)

            // ── Viewfinder ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // SurfaceView
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    surfaceHolder = holder
                                    viewModel.startPreview(holder.surface)
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int
                                ) {
                                    // Preview será recriado se necessário
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    surfaceHolder = null
                                }
                            })
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                )

                // Overlay de estado (loading)
                if (previewState is PreviewState.Starting) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AccentBlue,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }

                // Flash de captura
                if (captureState is CaptureState.Capturing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }

                // Erro
                if (previewState is PreviewState.Error) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚠️ ${previewState.message}",
                            color = StatusRed,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // ── Controls area ──
            ControlsArea(
                viewModel = viewModel,
                captureState = captureState,
                currentLensIndex = currentLensIndex,
                availableLenses = availableLenses,
                surfaceHolder = surfaceHolder
            )
        }
    }
}

// ─── Top Bar ────────────────────────────────────────────────

@Composable
private fun TopBar(onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "OpticCore",
            color = AccentBlue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        IconButton(onClick = onInfoClick) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Info de câmeras",
                tint = TextSecondary
            )
        }
    }
}

// ─── Controls Area ──────────────────────────────────────────

@Composable
private fun ControlsArea(
    viewModel: CameraViewModel,
    captureState: CaptureState,
    currentLensIndex: Int,
    availableLenses: List<CameraViewModel.LensInfo>,
    surfaceHolder: SurfaceHolder?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Barra de lentes ──
        LensSelectorBar(
            lenses = availableLenses,
            selectedIndex = currentLensIndex,
            onLensSelected = { index ->
                surfaceHolder?.surface?.let { surface ->
                    if (surface.isValid) {
                        viewModel.switchLens(index, surface)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Shutter + Status ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail da última foto
            LastPhotoThumbnail(captureState = captureState)

            // Botão shutter
            ShutterButton(
                isCapturing = captureState is CaptureState.Capturing,
                onClick = { viewModel.capturePhoto() }
            )

            // Placeholder para simetria
            Spacer(modifier = Modifier.size(56.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─── Lens Selector ──────────────────────────────────────────

@Composable
private fun LensSelectorBar(
    lenses: List<CameraViewModel.LensInfo>,
    selectedIndex: Int,
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
                        if (isSelected) AccentBlue.copy(alpha = 0.2f)
                        else Color.Transparent
                    )
                    .clickable { onLensSelected(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lens.label,
                    color = if (isSelected) AccentBlue else TextTertiary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ─── Shutter Button ─────────────────────────────────────────

@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = tween(100),
        label = "shutter_scale"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(3.dp, Color.White, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (!isCapturing) onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    if (isCapturing) Color.Gray else Color.White
                )
        )
    }
}

// ─── Last Photo Thumbnail ───────────────────────────────────

@Composable
private fun LastPhotoThumbnail(captureState: CaptureState) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCardElevated)
            .border(1.dp, DarkCard, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (captureState) {
            is CaptureState.Saved -> {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Foto salva",
                    tint = StatusGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
            is CaptureState.Capturing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AccentBlue,
                    strokeWidth = 2.dp
                )
            }
            is CaptureState.Error -> {
                Text("!", color = StatusRed, fontWeight = FontWeight.Bold)
            }
            else -> {
                // Vazio quando idle
            }
        }
    }
}

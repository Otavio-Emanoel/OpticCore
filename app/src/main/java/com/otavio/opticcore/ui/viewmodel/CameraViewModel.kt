package com.otavio.opticcore.ui.viewmodel

import android.app.Application
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.otavio.opticcore.data.hardware.CameraHardwareScanner
import com.otavio.opticcore.data.hardware.CameraSessionManager
import com.otavio.opticcore.data.hardware.ImageCaptureManager
import com.otavio.opticcore.data.model.CaptureState
import com.otavio.opticcore.data.model.LensType
import com.otavio.opticcore.data.model.PreviewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel que gerencia o estado do viewfinder e captura.
 * Coordena CameraSessionManager e ImageCaptureManager.
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OpticCore.CamVM"
    }

    val sessionManager = CameraSessionManager(application.applicationContext)
    private val captureManager = ImageCaptureManager(application.applicationContext)
    private val scanner = CameraHardwareScanner(application.applicationContext)

    // ─── State Flows ────────────────────────────────────────

    private val _previewState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _currentLensIndex = MutableStateFlow(0)
    val currentLensIndex: StateFlow<Int> = _currentLensIndex.asStateFlow()

    private val _lastPhotoUri = MutableStateFlow<Uri?>(null)
    val lastPhotoUri: StateFlow<Uri?> = _lastPhotoUri.asStateFlow()

    // Lentes disponíveis: Pair(cameraId, LensType)
    private val _availableLenses = MutableStateFlow<List<LensInfo>>(emptyList())
    val availableLenses: StateFlow<List<LensInfo>> = _availableLenses.asStateFlow()

    data class LensInfo(
        val cameraId: String,
        val lensType: LensType,
        val label: String
    )

    init {
        buildAvailableLenses()
        setupCallbacks()
    }

    // ─── Lentes disponíveis ─────────────────────────────────

    private fun buildAvailableLenses() {
        try {
            val report = scanner.scanAllCameras()
            val lenses = report.lenses.map { lens ->
                LensInfo(
                    cameraId = lens.cameraId,
                    lensType = lens.lensType,
                    label = lens.lensType.symbol
                )
            }
            _availableLenses.value = lenses
            Log.i(TAG, "Lentes disponíveis: ${lenses.map { "${it.label}(ID=${it.cameraId})" }}")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao construir lista de lentes: ${e.message}")
            // Fallback: usar IDs brutos
            val ids = sessionManager.getAvailableCameraIds()
            _availableLenses.value = ids.mapIndexed { index, id ->
                val facing = sessionManager.getCameraFacing(id)
                val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT
                LensInfo(
                    cameraId = id,
                    lensType = if (isFront) LensType.FRONT else LensType.WIDE,
                    label = if (isFront) "Front" else "${index + 1}x"
                )
            }
        }
    }

    // ─── Callbacks ──────────────────────────────────────────

    private fun setupCallbacks() {
        sessionManager.onPreviewStarted = {
            _previewState.value = PreviewState.Active
        }

        sessionManager.onPreviewError = { msg ->
            _previewState.value = PreviewState.Error(msg)
        }

        sessionManager.onImageCaptured = { reader ->
            viewModelScope.launch(Dispatchers.IO) {
                val result = captureManager.saveImageFromReader(reader)
                if (result != null) {
                    val (uri, name) = result
                    _lastPhotoUri.value = uri
                    _captureState.value = CaptureState.Saved(uri, name)
                    Log.i(TAG, "📷 Foto salva: $name")

                    // Reset capture state depois de 2s
                    delay(2000)
                    _captureState.value = CaptureState.Idle
                } else {
                    _captureState.value = CaptureState.Error("Falha ao salvar foto")
                    delay(2000)
                    _captureState.value = CaptureState.Idle
                }
            }
        }
    }

    // ─── Iniciar preview ────────────────────────────────────

    fun startPreview(surface: Surface) {
        val lenses = _availableLenses.value
        if (lenses.isEmpty()) {
            _previewState.value = PreviewState.Error("Nenhuma câmera disponível")
            return
        }

        _previewState.value = PreviewState.Starting
        val lens = lenses[_currentLensIndex.value]
        sessionManager.openCamera(lens.cameraId, surface)
    }

    // ─── Trocar lente ───────────────────────────────────────

    fun switchLens(index: Int, surface: Surface) {
        val lenses = _availableLenses.value
        if (index < 0 || index >= lenses.size) return
        if (index == _currentLensIndex.value) return

        _currentLensIndex.value = index
        _previewState.value = PreviewState.Starting
        val lens = lenses[index]
        Log.i(TAG, "Trocando para: ${lens.label} (ID=${lens.cameraId})")
        sessionManager.switchCamera(lens.cameraId, surface)
    }

    // ─── Captura ────────────────────────────────────────────

    fun capturePhoto() {
        if (_captureState.value is CaptureState.Capturing) return

        _captureState.value = CaptureState.Capturing
        sessionManager.captureStillImage()
    }

    // ─── Obter tamanho do preview ───────────────────────────

    fun getPreviewSize(): android.util.Size {
        val lenses = _availableLenses.value
        val currentLens = lenses.getOrNull(_currentLensIndex.value)
        val cameraId = currentLens?.cameraId ?: "0"
        return sessionManager.getPreviewSize(cameraId)
    }

    // ─── Cleanup ────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        sessionManager.release()
        Log.i(TAG, "ViewModel cleared, recursos liberados")
    }
}

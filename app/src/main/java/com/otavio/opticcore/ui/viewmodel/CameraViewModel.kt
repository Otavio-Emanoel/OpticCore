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
import com.otavio.opticcore.data.model.ProcessingState
import com.otavio.opticcore.data.processing.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel que gerencia viewfinder, captura e processamento.
 * Coordena CameraSessionManager, ImageCaptureManager e ImageProcessor.
 *
 * Fase 5: lifecycle-safe — cancelamento automático de jobs ao minimizar.
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OpticCore.CamVM"
    }

    val sessionManager = CameraSessionManager(application.applicationContext)
    private val captureManager = ImageCaptureManager(application.applicationContext)
    private val scanner = CameraHardwareScanner(application.applicationContext)

    // Job de processamento atual (cancelável)
    private var processingJob: Job? = null

    // ─── State Flows ────────────────────────────────────────

    private val _previewState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _currentLensIndex = MutableStateFlow(0)
    val currentLensIndex: StateFlow<Int> = _currentLensIndex.asStateFlow()

    private val _lastPhotoUri = MutableStateFlow<Uri?>(null)
    val lastPhotoUri: StateFlow<Uri?> = _lastPhotoUri.asStateFlow()

    // Configurações de processamento
    private val _processingSettings = MutableStateFlow(ImageProcessor.Settings())
    val processingSettings: StateFlow<ImageProcessor.Settings> = _processingSettings.asStateFlow()

    // Toggle do painel de ajustes
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    // Lentes disponíveis
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
            // Extrair bytes no handler thread (rápido)
            val jpegBytes = captureManager.extractJpegBytes(reader)

            if (jpegBytes != null) {
                // Processar e salvar em coroutine (cancelável, lifecycle-safe)
                processingJob = viewModelScope.launch(Dispatchers.Default) {
                    try {
                        _processingState.value = ProcessingState.Processing(0f)

                        val result = captureManager.processAndSave(
                            jpegBytes = jpegBytes,
                            settings = _processingSettings.value,
                            onProgress = { progress ->
                                _processingState.value = ProcessingState.Processing(progress)
                            }
                        )

                        if (result != null) {
                            val (uri, name) = result
                            _lastPhotoUri.value = uri
                            _captureState.value = CaptureState.Saved(uri, name)
                            _processingState.value = ProcessingState.Done
                            Log.i(TAG, "📷 Foto processada e salva: $name")

                            delay(2500)
                            _captureState.value = CaptureState.Idle
                            _processingState.value = ProcessingState.Idle
                        } else {
                            _processingState.value = ProcessingState.Error("Falha ao processar/salvar")
                            _captureState.value = CaptureState.Error("Falha")
                            delay(2500)
                            _captureState.value = CaptureState.Idle
                            _processingState.value = ProcessingState.Idle
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Log.w(TAG, "⚠️ Processamento cancelado (app minimizado?)")
                        _processingState.value = ProcessingState.Idle
                        _captureState.value = CaptureState.Idle
                        throw e // Re-throw para manter o contrato de cancellation
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro no processamento: ${e.message}", e)
                        _processingState.value = ProcessingState.Error(e.message ?: "Erro")
                        _captureState.value = CaptureState.Error(e.message ?: "Erro")
                        delay(2500)
                        _processingState.value = ProcessingState.Idle
                        _captureState.value = CaptureState.Idle
                    }
                }
            } else {
                _captureState.value = CaptureState.Error("Falha ao capturar frame")
                viewModelScope.launch {
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
        if (_processingState.value is ProcessingState.Processing) return

        _captureState.value = CaptureState.Capturing
        sessionManager.captureStillImage()
    }

    // ─── Settings de processamento ──────────────────────────

    fun updateSettings(update: (ImageProcessor.Settings) -> ImageProcessor.Settings) {
        _processingSettings.value = update(_processingSettings.value)
    }

    fun toggleSettings() {
        _showSettings.value = !_showSettings.value
    }

    fun resetSettings() {
        _processingSettings.value = ImageProcessor.Settings()
        Log.i(TAG, "Settings resetados para defaults")
    }

    // ─── Preview size ───────────────────────────────────────

    fun getPreviewSize(): android.util.Size {
        val lenses = _availableLenses.value
        val currentLens = lenses.getOrNull(_currentLensIndex.value)
        val cameraId = currentLens?.cameraId ?: "0"
        return sessionManager.getPreviewSize(cameraId)
    }

    // ─── Cleanup (Fase 5: lifecycle-safe) ───────────────────

    override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
        sessionManager.release()
        Log.i(TAG, "ViewModel cleared, jobs cancelados, recursos liberados")
    }
}

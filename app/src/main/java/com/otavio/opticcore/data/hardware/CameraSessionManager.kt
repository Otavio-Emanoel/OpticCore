package com.otavio.opticcore.data.hardware

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.graphics.ImageFormat

/**
 * Gerencia o ciclo de vida completo da Camera2 API:
 * - Abertura/fechamento de câmera
 * - Sessão de preview
 * - Captura de imagem estática
 * - Troca de lentes
 *
 * Implementação dos requisitos RF02, RF03, RF06.
 */
class CameraSessionManager(private val context: Context) {

    companion object {
        private const val TAG = "OpticCore.Session"
    }

    // ─── Estado interno ─────────────────────────────────────
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null

    private val handlerThread = HandlerThread("OpticCore.CameraThread").also { it.start() }
    val cameraHandler = Handler(handlerThread.looper)

    var currentCameraId: String? = null
        private set

    // ─── Callbacks externos ─────────────────────────────────
    var onPreviewStarted: (() -> Unit)? = null
    var onPreviewError: ((String) -> Unit)? = null
    var onCameraClosed: (() -> Unit)? = null
    var onImageCaptured: ((ImageReader) -> Unit)? = null

    // ─── Abrir câmera ───────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun openCamera(cameraId: String, previewSurface: Surface) {
        Log.i(TAG, "openCamera(ID=$cameraId)")

        // Fechar sessão anterior se existir
        closeSession()

        this.previewSurface = previewSurface
        this.currentCameraId = cameraId

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.i(TAG, "✅ Câmera $cameraId aberta com sucesso")
                    cameraDevice = camera
                    createPreviewSession(camera, previewSurface)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "⚠️ Câmera $cameraId desconectada")
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    val errorMsg = mapCameraError(error)
                    Log.e(TAG, "❌ Erro ao abrir câmera $cameraId: $errorMsg (code=$error)")
                    camera.close()
                    cameraDevice = null
                    onPreviewError?.invoke(errorMsg)
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao abrir câmera: ${e.message}", e)
            onPreviewError?.invoke(e.message ?: "Erro desconhecido")
        }
    }

    // ─── Preview Session ────────────────────────────────────

    private fun createPreviewSession(camera: CameraDevice, surface: Surface) {
        Log.i(TAG, "Criando sessão de preview...")

        try {
            // Configura ImageReader para captura JPEG
            val characteristics = cameraManager.getCameraCharacteristics(currentCameraId!!)
            val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = streamConfigMap?.getOutputSizes(ImageFormat.JPEG)
            val captureSize = jpegSizes?.maxByOrNull { it.width.toLong() * it.height.toLong() }
                ?: Size(1920, 1080)

            imageReader?.close()
            imageReader = ImageReader.newInstance(
                captureSize.width, captureSize.height,
                ImageFormat.JPEG, 2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    onImageCaptured?.invoke(reader)
                }, cameraHandler)
            }

            Log.i(TAG, "ImageReader configurado: ${captureSize.width}x${captureSize.height} JPEG")

            // Builder do preview
            previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                // AF contínuo (RF06)
                set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                // AE automático
                set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            }

            val surfaces = listOf(surface, imageReader!!.surface)

            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return

                    captureSession = session
                    try {
                        session.setRepeatingRequest(
                            previewRequestBuilder!!.build(),
                            null,
                            cameraHandler
                        )
                        Log.i(TAG, "✅ Preview ativo — AF_CONTINUOUS_PICTURE")
                        onPreviewStarted?.invoke()
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao iniciar repeating request: ${e.message}", e)
                        onPreviewError?.invoke(e.message ?: "Erro no preview")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "❌ Falha ao configurar sessão de captura")
                    onPreviewError?.invoke("Falha ao configurar sessão de captura")
                }
            }, cameraHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao criar preview session: ${e.message}", e)
            onPreviewError?.invoke(e.message ?: "Erro ao criar preview")
        }
    }

    // ─── Captura de foto ────────────────────────────────────

    fun captureStillImage(onCaptureCompleted: (() -> Unit)? = null) {
        val camera = cameraDevice ?: run {
            Log.e(TAG, "captureStillImage: cameraDevice é null")
            return
        }
        val session = captureSession ?: run {
            Log.e(TAG, "captureStillImage: captureSession é null")
            return
        }
        val reader = imageReader ?: run {
            Log.e(TAG, "captureStillImage: imageReader é null")
            return
        }

        Log.i(TAG, "📸 Disparando captura...")

        try {
            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                // Copiar configurações do preview
                set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                // JPEG de qualidade máxima
                set(CaptureRequest.JPEG_QUALITY, 100.toByte())

                // A MÁGICA PARA TIRAR A "CARA DE XIAOMI":
                // 1. Desliga a nitidez artificial (Edge Enhancement)
                set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF)
                // 2. Desliga a redução de ruído que deixa a pele parecendo cera
                set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_OFF)
                // 3. Pede uma curva de tons reta (sem contraste adicionado pelo hardware)
                set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_FAST)
            }

            // Pausar preview, capturar, retomar preview
            session.stopRepeating()

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    Log.i(TAG, "✅ Captura concluída")
                    onCaptureCompleted?.invoke()

                    // Retomar preview
                    try {
                        previewRequestBuilder?.let {
                            session.setRepeatingRequest(it.build(), null, cameraHandler)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao retomar preview: ${e.message}")
                    }
                }
            }, cameraHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao capturar: ${e.message}", e)
        }
    }

    // ─── Troca de lente ─────────────────────────────────────

    fun switchCamera(newCameraId: String, surface: Surface) {
        if (newCameraId == currentCameraId) {
            Log.i(TAG, "switchCamera: já estamos no ID=$newCameraId, ignorando")
            return
        }
        Log.i(TAG, "🔄 Trocando de câmera $currentCameraId → $newCameraId")
        openCamera(newCameraId, surface)
    }

    // ─── Obter resolução do preview ─────────────────────────

    fun getPreviewSize(cameraId: String): Size {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val previewSizes = streamConfigMap?.getOutputSizes(android.graphics.SurfaceTexture::class.java)

        // Escolhe a maior resolução de preview que cabe em ~1920x1080
        val targetRatio = 4.0 / 3.0
        val ideal = previewSizes
            ?.filter { it.width <= 1920 && it.height <= 1440 }
            ?.minByOrNull { Math.abs(it.width.toDouble() / it.height.toDouble() - targetRatio) }

        return ideal ?: Size(1920, 1080)
    }

    // ─── Obter IDs disponíveis ──────────────────────────────

    fun getAvailableCameraIds(): Array<String> = cameraManager.cameraIdList

    fun getCameraFacing(cameraId: String): Int? {
        return try {
            cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.LENS_FACING)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retorna o SENSOR_ORIENTATION da câmera (0, 90, 180, 270).
     * Necessário para rotacionar a foto corretamente.
     */
    fun getSensorOrientation(cameraId: String): Int {
        return try {
            cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        } catch (e: Exception) {
            90 // Default para maioria dos dispositivos
        }
    }

    /**
     * Verifica se a câmera é frontal.
     * Necessário para desfazer o espelhamento.
     */
    fun isFrontCamera(cameraId: String): Boolean {
        return getCameraFacing(cameraId) == CameraCharacteristics.LENS_FACING_FRONT
    }

    // ─── Cleanup ────────────────────────────────────────────

    private fun closeSession() {
        try {
            captureSession?.stopRepeating()
        } catch (_: Exception) {}

        captureSession?.close()
        captureSession = null

        cameraDevice?.close()
        cameraDevice = null

        imageReader?.close()
        imageReader = null

        Log.i(TAG, "Sessão anterior fechada")
    }

    fun release() {
        Log.i(TAG, "release() — Liberando todos os recursos")
        closeSession()
        previewSurface = null

        handlerThread.quitSafely()
        try {
            handlerThread.join(1000)
        } catch (_: InterruptedException) {}

        onCameraClosed?.invoke()
        Log.i(TAG, "✅ Recursos liberados")
    }

    // ─── Helpers ────────────────────────────────────────────

    private fun mapCameraError(error: Int): String = when (error) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "Câmera em uso por outro app"
        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "Máximo de câmeras abertas atingido"
        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "Câmera desabilitada por política"
        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "Erro fatal do hardware"
        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "Erro no serviço de câmera do sistema"
        else -> "Erro desconhecido ($error)"
    }
}

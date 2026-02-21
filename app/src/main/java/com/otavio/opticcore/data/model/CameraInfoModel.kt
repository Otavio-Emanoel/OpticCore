package com.otavio.opticcore.data.model

import android.util.Size

/**
 * Representa as informações de capacidade de uma lente individual do dispositivo.
 * Mapeamento direto das propriedades extraídas da Camera2 API.
 */
data class CameraLensInfo(
    val cameraId: String,
    val lensFacing: LensFacing,
    val focalLengths: List<Float>,
    val apertures: List<Float>,
    val sensorSize: SensorSize,
    val megapixels: Float,
    val supportedResolutions: List<Size>,
    val rawSupported: Boolean,
    val yuvSupported: Boolean,
    val hardwareLevel: HardwareLevel,
    val maxZoom: Float,
    val opticalStabilization: Boolean,
    val focusDistanceRange: ClosedFloatingPointRange<Float>?,
    val isoRange: IntRange?,
    val exposureTimeRange: LongRange?,
    val physicalCameraIds: List<String>,
    val lensType: LensType
)

/**
 * Direção da lente.
 */
enum class LensFacing {
    FRONT, BACK, EXTERNAL, UNKNOWN
}

/**
 * Nível de suporte do hardware da câmera, correspondente a
 * CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
 */
enum class HardwareLevel {
    LIMITED, FULL, LEGACY, LEVEL_3, EXTERNAL, UNKNOWN
}

/**
 * Tipo deduzido da lente com base na distância focal e outras propriedades.
 */
enum class LensType(val displayName: String, val symbol: String) {
    ULTRAWIDE("Ultra-Angular", "0.6x"),
    WIDE("Principal", "1x"),
    TELEPHOTO("Teleobjetiva", "2x"),
    MACRO("Macro", "Macro"),
    FRONT("Frontal", "Front"),
    UNKNOWN("Desconhecida", "?")
}

/**
 * Tamanho físico do sensor em milímetros.
 */
data class SensorSize(
    val widthMm: Float,
    val heightMm: Float
) {
    val diagonalMm: Float
        get() = kotlin.math.sqrt(widthMm * widthMm + heightMm * heightMm)

    override fun toString(): String =
        "%.2f x %.2f mm (diagonal: %.2f mm)".format(widthMm, heightMm, diagonalMm)
}

/**
 * Relatório completo do hardware de câmera do dispositivo.
 */
data class CameraDeviceReport(
    val deviceModel: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val totalCameras: Int,
    val lenses: List<CameraLensInfo>,
    val scanTimestamp: Long = System.currentTimeMillis()
)

/**
 * Estado do preview da câmera.
 */
sealed class PreviewState {
    data object Idle : PreviewState()
    data object Starting : PreviewState()
    data object Active : PreviewState()
    data class Error(val message: String) : PreviewState()
}

/**
 * Estado da captura de foto.
 */
sealed class CaptureState {
    data object Idle : CaptureState()
    data object Capturing : CaptureState()
    data class Saved(val uri: android.net.Uri, val path: String) : CaptureState()
    data class Error(val message: String) : CaptureState()
}

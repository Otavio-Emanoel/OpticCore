package com.otavio.opticcore.data.hardware

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Log
import android.util.Size
import com.otavio.opticcore.data.model.CameraDeviceReport
import com.otavio.opticcore.data.model.CameraLensInfo
import com.otavio.opticcore.data.model.HardwareLevel
import com.otavio.opticcore.data.model.LensFacing
import com.otavio.opticcore.data.model.LensType
import com.otavio.opticcore.data.model.SensorSize

/**
 * Scanner de hardware de câmera que utiliza a Camera2 API para
 * identificar e catalogar todas as capacidades de todas as lentes
 * físicas e lógicas do dispositivo.
 *
 * Implementação direta do RF01 — Mapeamento de Hardware.
 */
class CameraHardwareScanner(private val context: Context) {

    companion object {
        private const val TAG = "OpticCore.HwScanner"

        // Distâncias focais de referência para classificação
        // Valores típicos em mm (equivalente a sensor)
        private const val ULTRAWIDE_THRESHOLD = 2.5f
        private const val TELEPHOTO_THRESHOLD = 5.0f
        private const val MACRO_MIN_FOCUS_DISTANCE = 10.0f // dioptrias
    }

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /**
     * Executa o scan completo de todo o hardware de câmera disponível.
     * Retorna um [CameraDeviceReport] com todas as lentes mapeadas.
     */
    fun scanAllCameras(): CameraDeviceReport {
        Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║  OpticCore — Hardware Scanner Iniciado")
        Log.i(TAG, "║  Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.i(TAG, "║  Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        Log.i(TAG, "╚══════════════════════════════════════════════════════════════")

        val cameraIds = cameraManager.cameraIdList
        Log.i(TAG, "Câmeras detectadas: ${cameraIds.size} IDs → ${cameraIds.toList()}")

        val lenses = cameraIds.mapNotNull { id ->
            try {
                scanCamera(id)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao escanear câmera ID=$id: ${e.message}", e)
                null
            }
        }

        val report = CameraDeviceReport(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            totalCameras = cameraIds.size,
            lenses = lenses
        )

        logSummary(report)
        return report
    }

    /**
     * Escaneia uma câmera individual pelo seu ID.
     */
    private fun scanCamera(cameraId: String): CameraLensInfo {
        val chars = cameraManager.getCameraCharacteristics(cameraId)

        // --- Facing ---
        val facingInt = chars.get(CameraCharacteristics.LENS_FACING)
        val facing = when (facingInt) {
            CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
            CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
            CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
            else -> LensFacing.UNKNOWN
        }

        // --- Distâncias focais ---
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.toList() ?: emptyList()

        // --- Aberturas ---
        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            ?.toList() ?: emptyList()

        // --- Tamanho do sensor ---
        val sensorSizeRect = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val sensorSize = if (sensorSizeRect != null) {
            SensorSize(sensorSizeRect.width, sensorSizeRect.height)
        } else {
            SensorSize(0f, 0f)
        }

        // --- Resoluções suportadas ---
        val streamConfigMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val yuvResolutions = streamConfigMap
            ?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.toList() ?: emptyList()

        val rawResolutions = streamConfigMap
            ?.getOutputSizes(ImageFormat.RAW_SENSOR)
            ?.toList() ?: emptyList()

        val jpegResolutions = streamConfigMap
            ?.getOutputSizes(ImageFormat.JPEG)
            ?.toList() ?: emptyList()

        // A melhor resolução para cálculo de megapixels
        val maxResolution = (yuvResolutions + rawResolutions + jpegResolutions)
            .maxByOrNull { it.width.toLong() * it.height.toLong() }
        val megapixels = if (maxResolution != null) {
            (maxResolution.width.toLong() * maxResolution.height.toLong()) / 1_000_000f
        } else {
            0f
        }

        // --- Hardware Level ---
        val hwLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> HardwareLevel.LIMITED
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> HardwareLevel.FULL
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> HardwareLevel.LEGACY
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> HardwareLevel.LEVEL_3
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> HardwareLevel.EXTERNAL
            else -> HardwareLevel.UNKNOWN
        }

        // --- Max Zoom ---
        val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f

        // --- OIS ---
        val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        val hasOis = oisModes != null && oisModes.any { it == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON }

        // --- Focus Distance ---
        val minFocusDistance = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        val focusDistanceRange = if (minFocusDistance != null && minFocusDistance > 0f) {
            0f..minFocusDistance
        } else null

        // --- ISO Range ---
        val isoRangeRaw = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val isoRange = if (isoRangeRaw != null) {
            isoRangeRaw.lower..isoRangeRaw.upper
        } else null

        // --- Exposure Time Range ---
        val exposureRangeRaw = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val exposureTimeRange = if (exposureRangeRaw != null) {
            exposureRangeRaw.lower..exposureRangeRaw.upper
        } else null

        // --- Physical Camera IDs ---
        val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            chars.physicalCameraIds.toList()
        } else {
            emptyList()
        }

        // --- Classificação do tipo de lente ---
        val lensType = classifyLens(facing, focalLengths, minFocusDistance)

        val lensInfo = CameraLensInfo(
            cameraId = cameraId,
            lensFacing = facing,
            focalLengths = focalLengths,
            apertures = apertures,
            sensorSize = sensorSize,
            megapixels = megapixels,
            supportedResolutions = jpegResolutions + yuvResolutions + rawResolutions,
            rawSupported = rawResolutions.isNotEmpty(),
            yuvSupported = yuvResolutions.isNotEmpty(),
            hardwareLevel = hwLevel,
            maxZoom = maxZoom,
            opticalStabilization = hasOis,
            focusDistanceRange = focusDistanceRange,
            isoRange = isoRange,
            exposureTimeRange = exposureTimeRange,
            physicalCameraIds = physicalIds,
            lensType = lensType
        )

        logCameraDetails(lensInfo)
        return lensInfo
    }

    /**
     * Classifica o tipo da lente baseado na distância focal e propriedades.
     */
    private fun classifyLens(
        facing: LensFacing,
        focalLengths: List<Float>,
        minFocusDistance: Float?
    ): LensType {
        if (facing == LensFacing.FRONT) return LensType.FRONT

        val primaryFocal = focalLengths.firstOrNull() ?: return LensType.UNKNOWN

        // Macro: distância de foco mínima muito alta (em dioptrias)
        if (minFocusDistance != null && minFocusDistance >= MACRO_MIN_FOCUS_DISTANCE && primaryFocal < TELEPHOTO_THRESHOLD) {
            return LensType.MACRO
        }

        return when {
            primaryFocal < ULTRAWIDE_THRESHOLD -> LensType.ULTRAWIDE
            primaryFocal >= TELEPHOTO_THRESHOLD -> LensType.TELEPHOTO
            else -> LensType.WIDE
        }
    }

    /**
     * Loga detalhadamente as capacidades de uma câmera individual.
     */
    private fun logCameraDetails(info: CameraLensInfo) {
        Log.i(TAG, "")
        Log.i(TAG, "┌─────────────────────────────────────────────────────────")
        Log.i(TAG, "│ 📷 CÂMERA ID: ${info.cameraId}")
        Log.i(TAG, "│ Tipo: ${info.lensType.displayName} (${info.lensType.symbol})")
        Log.i(TAG, "│ Facing: ${info.lensFacing}")
        Log.i(TAG, "│ Hardware Level: ${info.hardwareLevel}")
        Log.i(TAG, "├─── Óptica ──────────────────────────────────────────────")
        Log.i(TAG, "│ Distância(s) Focal(is): ${info.focalLengths.joinToString(", ") { "%.2f mm".format(it) }}")
        Log.i(TAG, "│ Abertura(s): ${info.apertures.joinToString(", ") { "f/%.1f".format(it) }}")
        Log.i(TAG, "│ OIS: ${if (info.opticalStabilization) "✅ Sim" else "❌ Não"}")
        Log.i(TAG, "│ Zoom Digital Máx: %.1fx".format(info.maxZoom))

        if (info.focusDistanceRange != null) {
            Log.i(TAG, "│ Distância de Foco: %.2f - %.2f dioptrias".format(
                info.focusDistanceRange.start, info.focusDistanceRange.endInclusive
            ))
        }

        Log.i(TAG, "├─── Sensor ──────────────────────────────────────────────")
        Log.i(TAG, "│ Tamanho Físico: ${info.sensorSize}")
        Log.i(TAG, "│ Megapixels: %.1f MP".format(info.megapixels))

        if (info.isoRange != null) {
            Log.i(TAG, "│ ISO Range: ${info.isoRange.first} - ${info.isoRange.last}")
        }

        if (info.exposureTimeRange != null) {
            val minMs = info.exposureTimeRange.first / 1_000_000.0
            val maxMs = info.exposureTimeRange.last / 1_000_000.0
            val maxSec = maxMs / 1_000.0
            Log.i(TAG, "│ Tempo de Exposição: %.3f ms - %.1f s".format(minMs, maxSec))
        }

        Log.i(TAG, "├─── Formatos ────────────────────────────────────────────")
        Log.i(TAG, "│ RAW Suportado: ${if (info.rawSupported) "✅ Sim" else "❌ Não"}")
        Log.i(TAG, "│ YUV Suportado: ${if (info.yuvSupported) "✅ Sim" else "❌ Não"}")

        Log.i(TAG, "├─── Resoluções (Top 5) ──────────────────────────────────")
        val uniqueResolutions = info.supportedResolutions
            .distinctBy { "${it.width}x${it.height}" }
            .sortedByDescending { it.width.toLong() * it.height.toLong() }
            .take(5)
        uniqueResolutions.forEachIndexed { index, size ->
            val mp = (size.width.toLong() * size.height.toLong()) / 1_000_000f
            Log.i(TAG, "│   ${index + 1}. ${size.width} x ${size.height} (%.1f MP)".format(mp))
        }

        if (info.physicalCameraIds.isNotEmpty()) {
            Log.i(TAG, "├─── Câmeras Físicas ─────────────────────────────────────")
            Log.i(TAG, "│ IDs: ${info.physicalCameraIds.joinToString(", ")}")
        }

        Log.i(TAG, "└─────────────────────────────────────────────────────────")
    }

    /**
     * Loga o resumo final do scan.
     */
    private fun logSummary(report: CameraDeviceReport) {
        Log.i(TAG, "")
        Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║  📊 RELATÓRIO FINAL — ${report.deviceModel}")
        Log.i(TAG, "╠══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║  Total de câmeras: ${report.totalCameras}")

        val backLenses = report.lenses.filter { it.lensFacing == LensFacing.BACK }
        val frontLenses = report.lenses.filter { it.lensFacing == LensFacing.FRONT }

        Log.i(TAG, "║  Traseiras: ${backLenses.size}")
        backLenses.forEach { lens ->
            Log.i(TAG, "║    → ${lens.lensType.displayName} (${lens.lensType.symbol}) | %.1f MP | f/${lens.apertures.firstOrNull() ?: "?"} | ${lens.hardwareLevel}".format(lens.megapixels))
        }

        Log.i(TAG, "║  Frontais: ${frontLenses.size}")
        frontLenses.forEach { lens ->
            Log.i(TAG, "║    → ${lens.lensType.displayName} | %.1f MP | f/${lens.apertures.firstOrNull() ?: "?"}".format(lens.megapixels))
        }

        val rawCapable = report.lenses.filter { it.rawSupported }
        if (rawCapable.isNotEmpty()) {
            Log.i(TAG, "║  ✅ RAW disponível em: ${rawCapable.map { it.cameraId }.joinToString(", ")}")
        } else {
            Log.i(TAG, "║  ⚠️ Nenhuma câmera suporta RAW_SENSOR")
        }

        Log.i(TAG, "╚══════════════════════════════════════════════════════════════")
    }
}

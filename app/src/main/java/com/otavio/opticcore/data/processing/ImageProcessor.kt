package com.otavio.opticcore.data.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Pipeline de processamento de imagem (RF05, RNF01).
 *
 * Fluxo:
 *   JPEG bytes → Bitmap → S-Curve (LUT) → ColorMatrix → JPEG bytes
 *
 * Todo o processamento pesado roda em Dispatchers.Default
 * com verificações de cancellation para lifecycle safety (Fase 5).
 */
class ImageProcessor {

    companion object {
        private const val TAG = "OpticCore.Processor"
        private const val JPEG_QUALITY = 100
    }

    private val toneCurve = ToneCurve()
    private val colorGrading = ColorGrading()

    /**
     * Configurações de processamento com defaults "premium look".
     */
    data class Settings(
        val shadows: Float = -0.12f,       // Escurece sombras levemente
        val highlights: Float = 0.08f,     // Protege realces
        val gamma: Float = 1.0f,           // Gamma neutro
        val temperature: Float = 8f,       // Leve warmth
        val saturation: Float = 1.12f,     // Saturação sutil
        val contrast: Float = 1.06f,       // Contraste sutil
        val enabled: Boolean = true        // Processing on/off
    )

    /**
     * Processa JPEG bytes completo com todos os estágios.
     *
     * @param jpegBytes  Bytes JPEG originais do ImageReader
     * @param settings   Configurações de processamento
     * @param onProgress Callback de progresso (0.0..1.0)
     * @return JPEG bytes processados, ou os originais se processing desabilitado
     */
    suspend fun process(
        jpegBytes: ByteArray,
        settings: Settings = Settings(),
        onProgress: ((Float) -> Unit)? = null
    ): ByteArray = withContext(Dispatchers.Default) {

        if (!settings.enabled) {
            Log.i(TAG, "Processamento desabilitado, retornando JPEG original")
            return@withContext jpegBytes
        }

        val startTime = System.currentTimeMillis()
        Log.i(TAG, "╔══════════════════════════════════════════")
        Log.i(TAG, "║  🧠 Iniciando processamento de imagem")
        Log.i(TAG, "║  Input: ${jpegBytes.size / 1024} KB")
        Log.i(TAG, "╠══════════════════════════════════════════")

        // ── Estágio 1: Decode ──────────────────────────────
        onProgress?.invoke(0.05f)
        coroutineContext.ensureActive()

        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
            ?: run {
                Log.e(TAG, "Falha ao decodificar JPEG")
                return@withContext jpegBytes
            }

        Log.i(TAG, "║  [1/4] Decode: ${bitmap.width}x${bitmap.height} (${bitmap.byteCount / 1024} KB)")
        onProgress?.invoke(0.15f)

        // ── Estágio 2: S-Curve (LUT) ───────────────────────
        coroutineContext.ensureActive()

        val lut = toneCurve.generateLUT(
            shadows = settings.shadows,
            highlights = settings.highlights,
            gamma = settings.gamma
        )

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        toneCurve.applyLUT(pixels, lut) { lutProgress ->
            onProgress?.invoke(0.15f + lutProgress * 0.35f) // 15% - 50%
        }

        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        Log.i(TAG, "║  [2/4] S-Curve: shadows=%.2f, highlights=%.2f".format(settings.shadows, settings.highlights))
        onProgress?.invoke(0.50f)

        // ── Estágio 3: ColorMatrix ─────────────────────────
        coroutineContext.ensureActive()

        val colorMatrix = colorGrading.buildColorMatrix(
            temperature = settings.temperature,
            saturation = settings.saturation,
            contrast = settings.contrast
        )

        val processed = colorGrading.applyTonemapping(bitmap, colorMatrix)

        // Liberar bitmap intermediário (RNF02)
        if (processed != bitmap) {
            bitmap.recycle()
        }

        Log.i(TAG, "║  [3/4] ColorMatrix: temp=%.0f, sat=%.2f, contrast=%.2f"
            .format(settings.temperature, settings.saturation, settings.contrast))
        onProgress?.invoke(0.75f)

        // ── Estágio 4: Encode JPEG ─────────────────────────
        coroutineContext.ensureActive()

        val outputStream = ByteArrayOutputStream()
        processed.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val resultBytes = outputStream.toByteArray()

        // Liberar bitmap final
        processed.recycle()

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "║  [4/4] Encode: ${resultBytes.size / 1024} KB (Q$JPEG_QUALITY)")
        Log.i(TAG, "╠══════════════════════════════════════════")
        Log.i(TAG, "║  ✅ Processamento concluído em ${elapsed}ms")
        Log.i(TAG, "║  Input: ${jpegBytes.size / 1024} KB → Output: ${resultBytes.size / 1024} KB")
        Log.i(TAG, "╚══════════════════════════════════════════")

        onProgress?.invoke(1.0f)
        resultBytes
    }
}

package com.otavio.opticcore.data.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * Pipeline de processamento de imagem nível iPhone (RF05, RNF01).
 *
 * Fluxo:
 *   JPEG bytes → Bitmap → Rotação/Flip → S-Curve → ColorMatrix
 *   → Local Contrast → Sharpening → Vignette → JPEG bytes
 *
 * Todo o processamento pesado roda em Dispatchers.Default
 * com verificações de cancellation para lifecycle safety.
 */
class ImageProcessor {

    companion object {
        private const val TAG = "OpticCore.Processor"
        private const val JPEG_QUALITY = 100
    }

    private val toneCurve = ToneCurve()
    private val colorGrading = ColorGrading()

    /**
     * Configurações de processamento com defaults "iPhone Look".
     * Estes valores emulam: Deep Fusion + Smart HDR + Photographic Styles.
     */
    data class Settings(
        // Tone Mapping (S-Curve)
        val shadows: Float = -0.18f,        // Crush suave nas sombras
        val highlights: Float = 0.14f,      // Protege realces generosamente
        val gamma: Float = 0.96f,           // Levíssimo lift nos midtones

        // Color Science
        val temperature: Float = 6f,        // Warmth sutil (pele dourada)
        val tint: Float = 2f,               // Leve shift magenta (remove verde de pele)
        val saturation: Float = 1.08f,      // Saturação controlada
        val vibrance: Float = 1.15f,        // Boost seletivo em cores dessaturadas
        val contrast: Float = 1.10f,        // Contraste médio-alto

        // Detail Enhancement
        val sharpenAmount: Float = 0.5f,    // Sharpening (0=off, 1=máximo)
        val clarity: Float = 0.15f,         // Local contrast (luminosity micro-contrast)

        // Vignette
        val vignetteStrength: Float = 0.12f,// Vinheta sutil para foco central

        // Rotação/espelhamento
        val sensorOrientation: Int = 90,    // Graus do sensor
        val isFrontCamera: Boolean = false, // Desfazer mirror se frontal

        val enabled: Boolean = true
    )

    /**
     * Processa JPEG bytes com pipeline completo nível iPhone.
     */
    suspend fun process(
        jpegBytes: ByteArray,
        settings: Settings = Settings(),
        onProgress: ((Float) -> Unit)? = null
    ): ByteArray = withContext(Dispatchers.Default) {

        if (!settings.enabled) {
            // Mesmo sem processamento, precisa corrigir orientação
            val rotated = fixOrientation(jpegBytes, settings)
            return@withContext rotated
        }

        val startTime = System.currentTimeMillis()
        Log.i(TAG, "╔══════════════════════════════════════════")
        Log.i(TAG, "║  🧠 Pipeline iPhone — Processamento")
        Log.i(TAG, "║  Input: ${jpegBytes.size / 1024} KB")
        Log.i(TAG, "╠══════════════════════════════════════════")

        // ── [1/7] Decode + Orientação ──────────────────────
        onProgress?.invoke(0.03f)
        coroutineContext.ensureActive()

        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
            ?: run {
                Log.e(TAG, "Falha ao decodificar JPEG")
                return@withContext jpegBytes
            }

        // Rotacionar e des-espelhar
        bitmap = applyOrientationFix(bitmap, settings.sensorOrientation, settings.isFrontCamera)

        Log.i(TAG, "║  [1/7] Decode + Orientação: ${bitmap.width}x${bitmap.height}")
        onProgress?.invoke(0.10f)

        // ── [2/7] S-Curve (Tone Mapping) ───────────────────
        coroutineContext.ensureActive()

        val lut = toneCurve.generateLUT(
            shadows = settings.shadows,
            highlights = settings.highlights,
            gamma = settings.gamma
        )

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        toneCurve.applyLUT(pixels, lut) { p ->
            onProgress?.invoke(0.10f + p * 0.20f) // 10%-30%
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        Log.i(TAG, "║  [2/7] S-Curve: shadows=%.2f, highlights=%.2f, gamma=%.2f"
            .format(settings.shadows, settings.highlights, settings.gamma))
        onProgress?.invoke(0.30f)

        // ── [3/7] Color Science (ColorMatrix) ──────────────
        coroutineContext.ensureActive()

        val colorMatrix = colorGrading.buildColorMatrix(
            temperature = settings.temperature,
            tint = settings.tint,
            saturation = settings.saturation,
            vibrance = settings.vibrance,
            contrast = settings.contrast
        )

        val colored = colorGrading.applyTonemapping(bitmap, colorMatrix)
        if (colored != bitmap) bitmap.recycle()
        bitmap = colored

        Log.i(TAG, "║  [3/7] Color Science: temp=%.0f, tint=%.0f, sat=%.2f, vib=%.2f, con=%.2f"
            .format(settings.temperature, settings.tint, settings.saturation, settings.vibrance, settings.contrast))
        onProgress?.invoke(0.45f)

        // ── [3.5/7] Face Detection (Motor IA) ──────────────
        coroutineContext.ensureActive()

        try {
            val faces = detectFaces(bitmap)
            if (faces.isNotEmpty()) {
                Log.i(TAG, "║  [3.5/7] Motor IA: ${faces.size} rosto(s) detectado(s)!")
                for ((index, face) in faces.withIndex()) {
                    Log.i(TAG, "║    Rosto $index: boundingBox=${face.boundingBox}")
                }
            } else {
                Log.i(TAG, "║  [3.5/7] Motor IA: Nenhum rosto detectado.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "║  [3.5/7] Motor IA: Erro na detecção facial: ${e.message}")
        }
        onProgress?.invoke(0.50f)

        // ── [4/7] Clarity (Local Contrast) ─────────────────
        coroutineContext.ensureActive()

        if (settings.clarity > 0f) {
            bitmap = applyClarity(bitmap, settings.clarity)
            Log.i(TAG, "║  [4/7] Clarity: %.2f".format(settings.clarity))
        } else {
            Log.i(TAG, "║  [4/7] Clarity: desativado")
        }
        onProgress?.invoke(0.55f)

        // ── [5/7] Sharpening (Unsharp Mask) ────────────────
        coroutineContext.ensureActive()

        if (settings.sharpenAmount > 0f) {
            bitmap = applyUnsharpMask(bitmap, settings.sharpenAmount)
            Log.i(TAG, "║  [5/7] Sharpening: %.2f".format(settings.sharpenAmount))
        } else {
            Log.i(TAG, "║  [5/7] Sharpening: desativado")
        }
        onProgress?.invoke(0.68f)

        // ── [6/7] Vignette ─────────────────────────────────
        coroutineContext.ensureActive()

        if (settings.vignetteStrength > 0f) {
            bitmap = applyVignette(bitmap, settings.vignetteStrength)
            Log.i(TAG, "║  [6/7] Vignette: %.2f".format(settings.vignetteStrength))
        } else {
            Log.i(TAG, "║  [6/7] Vignette: desativado")
        }
        onProgress?.invoke(0.80f)

        // ── [7/7] Encode JPEG ──────────────────────────────
        coroutineContext.ensureActive()

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val resultBytes = outputStream.toByteArray()
        bitmap.recycle()

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "║  [7/7] Encode: ${resultBytes.size / 1024} KB (Q$JPEG_QUALITY)")
        Log.i(TAG, "╠══════════════════════════════════════════")
        Log.i(TAG, "║  ✅ Pipeline concluído em ${elapsed}ms")
        Log.i(TAG, "║  Input: ${jpegBytes.size / 1024} KB → Output: ${resultBytes.size / 1024} KB")
        Log.i(TAG, "╚══════════════════════════════════════════")

        onProgress?.invoke(1.0f)
        resultBytes
    }

    // ═══════════════════════════════════════════════════════════
    //  Motor de IA (Visão Computacional)
    // ═══════════════════════════════════════════════════════════

    /**
     * Detecta rostos na imagem usando Google ML Kit Face Detection.
     * Necessário para aplicar o filtro bilateral (embelezamento) apenas no rosto,
     * preservando olhos, boca e cabelo.
     */
    private suspend fun detectFaces(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { continuation ->
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // Achar olhos, boca
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)   // Achar o contorno exato do rosto
            .build()

        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (continuation.isActive) {
                    continuation.resume(faces)
                }
                detector.close()
            }
            .addOnFailureListener { e ->
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
                detector.close()
            }

        // Se a coroutine for cancelada (ex: usuário fechou o app), liberamos os recursos
        continuation.invokeOnCancellation {
            detector.close()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Orientação
    // ═══════════════════════════════════════════════════════════

    /**
     * Corrige orientação sem processar (para quando processing é OFF).
     */
    private fun fixOrientation(jpegBytes: ByteArray, settings: Settings): ByteArray {
        val options = BitmapFactory.Options().apply { inMutable = true }
        var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
            ?: return jpegBytes

        bitmap = applyOrientationFix(bitmap, settings.sensorOrientation, settings.isFrontCamera)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /**
     * Rotaciona e des-espelha o Bitmap baseado nos dados do sensor.
     */
    private fun applyOrientationFix(
        source: Bitmap,
        sensorOrientation: Int,
        isFrontCamera: Boolean
    ): Bitmap {
        val matrix = Matrix()

        // Rotação baseada na orientação do sensor
        if (sensorOrientation != 0) {
            matrix.postRotate(sensorOrientation.toFloat())
        }

        // Frontal: desfaz o espelhamento horizontal
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, source.width / 2f, source.height / 2f)
            // Se rotacionou, o centro mudou — recalcular
            if (sensorOrientation == 90 || sensorOrientation == 270) {
                matrix.postScale(-1f, 1f, source.height / 2f, source.width / 2f)
                // A escala dupla se cancela, então fazemos direto:
                matrix.reset()
                matrix.postRotate(sensorOrientation.toFloat())
                matrix.postScale(-1f, 1f) // Mirror após rotação
            }
        }

        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result != source) source.recycle()

        Log.d(TAG, "Orientação: rotation=${sensorOrientation}°, front=$isFrontCamera → ${result.width}x${result.height}")
        return result
    }

    // ═══════════════════════════════════════════════════════════
    //  Clarity (Local Contrast Enhancement)
    // ═══════════════════════════════════════════════════════════

    /**
     * Aplica micro-contraste local via overlay blend com versão desfocada.
     * Técnica similar ao "Clarity" do Lightroom / iPhone Deep Fusion.
     */
    private fun applyClarity(source: Bitmap, amount: Float): Bitmap {
        // Downscale para blur rápido
        val scale = 0.25f
        val smallW = (source.width * scale).toInt()
        val smallH = (source.height * scale).toInt()

        val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
        val blurred = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        small.recycle()

        // Blend: pixels = source + amount * (source - blurred)
        val srcPixels = IntArray(source.width * source.height)
        val blrPixels = IntArray(srcPixels.size)
        source.getPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)
        blurred.getPixels(blrPixels, 0, source.width, 0, 0, source.width, source.height)
        blurred.recycle()

        for (i in srcPixels.indices) {
            val s = srcPixels[i]
            val b = blrPixels[i]

            val sr = (s shr 16) and 0xFF
            val sg = (s shr 8) and 0xFF
            val sb = s and 0xFF

            val br = (b shr 16) and 0xFF
            val bg = (b shr 8) and 0xFF
            val bb = b and 0xFF

            val nr = (sr + amount * (sr - br)).toInt().coerceIn(0, 255)
            val ng = (sg + amount * (sg - bg)).toInt().coerceIn(0, 255)
            val nb = (sb + amount * (sb - bb)).toInt().coerceIn(0, 255)

            srcPixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)
        source.recycle()
        return result
    }

    // ═══════════════════════════════════════════════════════════
    //  Sharpening (Unsharp Mask)
    // ═══════════════════════════════════════════════════════════

    /**
     * Aplica Unsharp Mask: sharp = original + amount * (original - blurred).
     * Técnica usada pelo iPhone para nitidez sem artefatos.
     */
    private fun applyUnsharpMask(source: Bitmap, amount: Float): Bitmap {
        // Blur leve para unsharp mask
        val scale = 0.5f
        val smallW = (source.width * scale).toInt()
        val smallH = (source.height * scale).toInt()

        val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
        val blurred = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        small.recycle()

        val srcPixels = IntArray(source.width * source.height)
        val blrPixels = IntArray(srcPixels.size)
        source.getPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)
        blurred.getPixels(blrPixels, 0, source.width, 0, 0, source.width, source.height)
        blurred.recycle()

        val strength = amount * 1.2f // Escala para resultado visível

        for (i in srcPixels.indices) {
            val s = srcPixels[i]
            val b = blrPixels[i]

            val sr = (s shr 16) and 0xFF
            val sg = (s shr 8) and 0xFF
            val sb = s and 0xFF

            val br = (b shr 16) and 0xFF
            val bg = (b shr 8) and 0xFF
            val bb = b and 0xFF

            // Unsharp mask: resultado = original + strength * (original - blur)
            val nr = (sr + strength * (sr - br)).toInt().coerceIn(0, 255)
            val ng = (sg + strength * (sg - bg)).toInt().coerceIn(0, 255)
            val nb = (sb + strength * (sb - bb)).toInt().coerceIn(0, 255)

            srcPixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)
        source.recycle()
        return result
    }

    // ═══════════════════════════════════════════════════════════
    //  Vignette
    // ═══════════════════════════════════════════════════════════

    /**
     * Aplica vinheta radial sutil para focar atenção no centro.
     * Técnica clássica de fotografia, usada sutilmente pelo iPhone.
     */
    private fun applyVignette(source: Bitmap, strength: Float): Bitmap {
        val w = source.width
        val h = source.height
        val cx = w / 2f
        val cy = h / 2f
        val maxDist = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val dx = x - cx
                val dy = y - cy
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat() / maxDist

                // Vinheta suave: começa a escurecer a partir de 50% da distância
                val factor = 1f - strength * (dist * dist)
                val clampedFactor = factor.coerceIn(0.4f, 1f) // Nunca vai totalmente preto

                val p = pixels[idx]
                val a = (p shr 24) and 0xFF
                val r = ((p shr 16) and 0xFF) * clampedFactor
                val g = ((p shr 8) and 0xFF) * clampedFactor
                val b = (p and 0xFF) * clampedFactor

                pixels[idx] = (a shl 24) or
                        (r.toInt().coerceIn(0, 255) shl 16) or
                        (g.toInt().coerceIn(0, 255) shl 8) or
                        b.toInt().coerceIn(0, 255)
            }
        }

        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        source.recycle()
        return result
    }
}

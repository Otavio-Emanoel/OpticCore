package com.otavio.opticcore.data.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log

/**
 * Color Grading avançado usando Android ColorMatrix.
 *
 * Combina múltiplas transformações numa única matriz:
 *   - Temperatura (balanço de branco warm/cool)
 *   - Tint (green/magenta shift — essencial para skin tones)
 *   - Saturação (luminance-preserving BT.709)
 *   - Vibrance (saturação seletiva — boost em cores dessaturadas)
 *   - Contraste
 *
 * RF05 — Processamento de Imagem Customizado.
 */
class ColorGrading {

    companion object {
        private const val TAG = "OpticCore.ColorGrade"

        // Coeficientes de luminância BT.709 (padrão sRGB/iPhone)
        private const val LUM_R = 0.2126f
        private const val LUM_G = 0.7152f
        private const val LUM_B = 0.0722f
    }

    /**
     * Cria uma ColorMatrix combinada com todos os ajustes.
     */
    fun buildColorMatrix(
        temperature: Float = 6f,
        tint: Float = 2f,
        saturation: Float = 1.08f,
        vibrance: Float = 1.15f,
        contrast: Float = 1.10f
    ): ColorMatrix {
        val combined = ColorMatrix()

        // 1. Temperatura (White Balance)
        if (temperature != 0f) {
            combined.postConcat(createTemperatureMatrix(temperature))
        }

        // 2. Tint (Green/Magenta)
        if (tint != 0f) {
            combined.postConcat(createTintMatrix(tint))
        }

        // 3. Contraste (antes da saturação para melhor resultado)
        if (contrast != 1.0f) {
            combined.postConcat(createContrastMatrix(contrast))
        }

        // 4. Saturação base
        if (saturation != 1.0f) {
            combined.postConcat(createSaturationMatrix(saturation))
        }

        // 5. Vibrance (saturação seletiva) — simulada via segunda aplicação
        if (vibrance != 1.0f && vibrance != saturation) {
            val vibranceAdjust = 1f + (vibrance - 1f) * 0.3f // Mais sutil
            combined.postConcat(createSaturationMatrix(vibranceAdjust))
        }

        Log.d(TAG, "ColorMatrix: temp=%.0f, tint=%.0f, sat=%.2f, vib=%.2f, con=%.2f"
            .format(temperature, tint, saturation, vibrance, contrast))

        return combined
    }

    /**
     * Aplica a ColorMatrix ao Bitmap usando Canvas + Paint.
     */
    fun applyTonemapping(source: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        Log.d(TAG, "ColorMatrix aplicada: ${source.width}x${source.height}")
        return result
    }

    // ─── Matrizes individuais ───────────────────────────────

    /**
     * Temperatura: warm/cool shift.
     * iPhone usa valores sutis para dourar skin tones sem puxar para laranja.
     */
    private fun createTemperatureMatrix(temperature: Float): ColorMatrix {
        val t = temperature / 100f

        // R sobe suavemente, B desce — emula tungsten/daylight
        val rShift = 1f + t * 0.12f
        val gShift = 1f + t * 0.04f
        val bShift = 1f - t * 0.14f

        return ColorMatrix(floatArrayOf(
            rShift, 0f,     0f,     0f, 0f,
            0f,     gShift, 0f,     0f, 0f,
            0f,     0f,     bShift, 0f, 0f,
            0f,     0f,     0f,     1f, 0f
        ))
    }

    /**
     * Tint: Green/Magenta shift.
     * Essencial para corrigir tons de pele que ficam muito verdes.
     * iPhone aplica tint automático baseado no AWB.
     */
    private fun createTintMatrix(tint: Float): ColorMatrix {
        val t = tint / 100f

        // Positivo = mais magenta (R+B up, G down)
        // Negativo = mais verde (G up, R+B down)
        val rShift = 1f + t * 0.06f
        val gShift = 1f - t * 0.08f
        val bShift = 1f + t * 0.04f

        return ColorMatrix(floatArrayOf(
            rShift, 0f,     0f,     0f, 0f,
            0f,     gShift, 0f,     0f, 0f,
            0f,     0f,     bShift, 0f, 0f,
            0f,     0f,     0f,     1f, 0f
        ))
    }

    /**
     * Saturação com preservação de luminância (BT.709).
     */
    private fun createSaturationMatrix(saturation: Float): ColorMatrix {
        val s = saturation
        val invS = 1f - s

        return ColorMatrix(floatArrayOf(
            invS * LUM_R + s, invS * LUM_G,     invS * LUM_B,     0f, 0f,
            invS * LUM_R,     invS * LUM_G + s, invS * LUM_B,     0f, 0f,
            invS * LUM_R,     invS * LUM_G,     invS * LUM_B + s, 0f, 0f,
            0f,               0f,               0f,               1f, 0f
        ))
    }

    /**
     * Contraste: escala + offset centrado em 128.
     */
    private fun createContrastMatrix(contrast: Float): ColorMatrix {
        val offset = 128f * (1f - contrast)

        return ColorMatrix(floatArrayOf(
            contrast, 0f,       0f,       0f, offset,
            0f,       contrast, 0f,       0f, offset,
            0f,       0f,       contrast, 0f, offset,
            0f,       0f,       0f,       1f, 0f
        ))
    }
}

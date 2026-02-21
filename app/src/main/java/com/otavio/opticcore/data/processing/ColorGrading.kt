package com.otavio.opticcore.data.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log

/**
 * Color Grading usando Android ColorMatrix.
 *
 * Combina múltiplas transformações numa única matriz para
 * aplicação eficiente via GPU (Canvas + Paint):
 *   - Temperatura (balanço de branco warm/cool)
 *   - Saturação (luminance-preserving)
 *   - Contraste
 *
 * RF05 — Processamento de Imagem Customizado.
 */
class ColorGrading {

    companion object {
        private const val TAG = "OpticCore.ColorGrade"

        // Coeficientes de luminância BT.709
        private const val LUM_R = 0.2126f
        private const val LUM_G = 0.7152f
        private const val LUM_B = 0.0722f
    }

    /**
     * Cria uma ColorMatrix combinada com todos os ajustes.
     *
     * @param temperature  -100 a +100 (negativo = cool/azul, positivo = warm/amarelo)
     * @param saturation   0.0 a 2.0 (0 = P&B, 1 = normal, 2 = super saturado)
     * @param contrast     0.5 a 2.0 (1.0 = normal)
     */
    fun buildColorMatrix(
        temperature: Float = 0f,
        saturation: Float = 1.1f,
        contrast: Float = 1.05f
    ): ColorMatrix {
        val combined = ColorMatrix()

        // 1. Temperatura (White Balance)
        if (temperature != 0f) {
            val tempMatrix = createTemperatureMatrix(temperature)
            combined.postConcat(tempMatrix)
        }

        // 2. Saturação
        if (saturation != 1.0f) {
            val satMatrix = createSaturationMatrix(saturation)
            combined.postConcat(satMatrix)
        }

        // 3. Contraste
        if (contrast != 1.0f) {
            val contrastMatrix = createContrastMatrix(contrast)
            combined.postConcat(contrastMatrix)
        }

        Log.d(TAG, "ColorMatrix construída: temp=%.0f, sat=%.2f, contrast=%.2f"
            .format(temperature, saturation, contrast))

        return combined
    }

    /**
     * Aplica a ColorMatrix ao Bitmap usando Canvas + Paint.
     * Usa aceleração de hardware quando disponível.
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
     * Temperatura: shift no R e B para simular warm/cool.
     * Ranges: -100 (cool blue) a +100 (warm orange)
     */
    private fun createTemperatureMatrix(temperature: Float): ColorMatrix {
        val t = temperature / 100f // Normaliza para -1..+1

        // Warm: aumenta R, diminui B
        // Cool: diminui R, aumenta B
        val rShift = 1f + t * 0.15f
        val gShift = 1f + t * 0.03f
        val bShift = 1f - t * 0.18f

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

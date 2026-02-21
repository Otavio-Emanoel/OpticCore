package com.otavio.opticcore.data.processing

import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Implementação de curva de tons (S-Curve) para tone mapping.
 *
 * Gera uma LUT (Lookup Table) de 256 entradas baseada numa
 * Bézier cúbica parametrizada, permitindo:
 *   - Escurecer sombras (crush blacks)
 *   - Proteger realces (preserve highlights)
 *   - Adicionar contraste seletivo
 *
 * RF05 — Processamento de Imagem Customizado.
 */
class ToneCurve {

    companion object {
        private const val TAG = "OpticCore.ToneCurve"
        private const val LUT_SIZE = 256
    }

    /**
     * Gera uma LUT de 256 entries baseada em parâmetros de sombra e realce.
     *
     * @param shadows  -1.0 a 1.0 (negativo = escurece, positivo = clareia sombras)
     * @param highlights -1.0 a 1.0 (negativo = escurece, positivo = protege/clareia realces)
     * @param gamma    ajuste de gamma (1.0 = neutro, <1 = clareia midtones, >1 = escurece midtones)
     */
    fun generateLUT(
        shadows: Float = -0.15f,
        highlights: Float = 0.10f,
        gamma: Float = 1.0f
    ): IntArray {
        val lut = IntArray(LUT_SIZE)

        // Pontos de controle da Bézier cúbica:
        // P0 = (0, 0 + shadowLift)
        // P1 = controle inferior (define a curva nas sombras)
        // P2 = controle superior (define a curva nos realces)
        // P3 = (1, 1 + highlightShift)

        val shadowLift = (-shadows * 0.15f).coerceIn(-0.2f, 0.2f)
        val highlightShift = (highlights * 0.12f).coerceIn(-0.15f, 0.15f)

        // Controles da S-Curve
        val p0y = (0f + shadowLift).coerceIn(0f, 0.3f)
        val p1x = 0.25f
        val p1y = (0.20f + shadows * 0.15f).coerceIn(0.05f, 0.45f)
        val p2x = 0.75f
        val p2y = (0.80f + highlights * 0.12f).coerceIn(0.55f, 0.95f)
        val p3y = (1.0f + highlightShift).coerceIn(0.85f, 1.0f)

        for (i in 0 until LUT_SIZE) {
            val t = i / 255f

            // Bézier cúbica: B(t) = (1-t)³P0 + 3(1-t)²tP1 + 3(1-t)t²P2 + t³P3
            val oneMinusT = 1f - t
            val value = (oneMinusT.pow(3) * p0y) +
                    (3f * oneMinusT.pow(2) * t * p1y) +
                    (3f * oneMinusT * t.pow(2) * p2y) +
                    (t.pow(3) * p3y)

            // Aplicar gamma
            val gammaAdjusted = if (gamma != 1.0f) {
                value.coerceIn(0f, 1f).pow(1f / gamma)
            } else {
                value
            }

            lut[i] = (gammaAdjusted * 255f).toInt().coerceIn(0, 255)
        }

        Log.d(TAG, "LUT gerada: shadows=%.2f, highlights=%.2f, gamma=%.2f".format(shadows, highlights, gamma))
        Log.d(TAG, "  Blacks: ${lut[0]}, Shadows: ${lut[64]}, Midtones: ${lut[128]}, Highlights: ${lut[192]}, Whites: ${lut[255]}")

        return lut
    }

    /**
     * Aplica a LUT a um array de pixels ARGB.
     * Opera in-place para economia de memória (RNF02).
     *
     * @param pixels Array de pixels ARGB extraído do Bitmap
     * @param lut    Lookup table de 256 entries
     * @param progressCallback Callback com progresso 0.0..1.0
     */
    fun applyLUT(
        pixels: IntArray,
        lut: IntArray,
        progressCallback: ((Float) -> Unit)? = null
    ) {
        val total = pixels.size
        val reportInterval = total / 20 // Reporta progresso a cada 5%

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel shr 24) and 0xFF
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            pixels[i] = (a shl 24) or
                    (lut[r] shl 16) or
                    (lut[g] shl 8) or
                    lut[b]

            if (reportInterval > 0 && i % reportInterval == 0) {
                progressCallback?.invoke(i.toFloat() / total)
            }
        }
    }
}

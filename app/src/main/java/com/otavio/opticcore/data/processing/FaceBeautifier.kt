package com.otavio.opticcore.data.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FaceBeautifier {

    companion object {
        private const val TAG = "OpticCore.FaceBeautifier"
    }

    suspend fun applyBeautyMagic(sourceBitmap: Bitmap): Bitmap {
        val faces = detectFaces(sourceBitmap)
        
        if (faces.isEmpty()) {
            Log.i(TAG, "Nenhum rosto detectado para embelezamento.")
            return sourceBitmap
        }

        Log.i(TAG, "${faces.size} rosto(s) detectado(s). Aplicando suavização...")

        val blurredBitmap = createSmoothSkinBitmap(sourceBitmap)
        
        // Tratar possível null no config do Bitmap (ex: hardware bitmaps)
        val config = sourceBitmap.config ?: Bitmap.Config.ARGB_8888
        val resultBitmap = sourceBitmap.copy(config, true) ?: return sourceBitmap
        val canvas = Canvas(resultBitmap)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        for (face in faces) {
            val bounds = face.boundingBox
            val expandedBounds = Rect(
                (bounds.left - bounds.width() * 0.1).toInt(),
                (bounds.top - bounds.height() * 0.2).toInt(),
                (bounds.right + bounds.width() * 0.1).toInt(),
                (bounds.bottom + bounds.height() * 0.1).toInt()
            )
            canvas.drawBitmap(blurredBitmap, expandedBounds, expandedBounds, paint)
        }
        
        blurredBitmap.recycle()
        return resultBitmap
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { continuation ->
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()

        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (continuation.isActive) continuation.resume(faces)
                detector.close()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erro na detecção facial: ${e.message}")
                if (continuation.isActive) continuation.resume(emptyList()) // Retorna vazio em caso de erro para não travar
                detector.close()
            }

        continuation.invokeOnCancellation {
            detector.close()
        }
    }

    private fun createSmoothSkinBitmap(source: Bitmap): Bitmap {
        // Um algoritmo de Bilateral Filter seria o ideal aqui.
        // Como aproximação rápida em Kotlin: faz um downscale agressivo e um upscale 
        // para "derreter" os poros, misturando com a imagem original.
        val scale = 0.2f
        val small = Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        val blurred = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        small.recycle()
        return blurred
    }
}

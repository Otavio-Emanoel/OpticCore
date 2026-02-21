package com.otavio.opticcore.data.hardware

import android.content.ContentValues
import android.content.Context
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.otavio.opticcore.data.processing.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gerencia a persistência de imagens capturadas.
 *
 * Fluxo atualizado (Fase 4):
 *   ImageReader → JPEG bytes → [ImageProcessor] → JPEG processado → MediaStore
 *
 * RF05 + RF07 — Processamento + Gravação em Disco.
 */
class ImageCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "OpticCore.Capture"
        private const val PHOTO_PREFIX = "OpticCore"
        private const val MIME_TYPE = "image/jpeg"
    }

    private val processor = ImageProcessor()

    /**
     * Extrai JPEG do ImageReader, retorna bytes brutos (sem processar).
     */
    fun extractJpegBytes(reader: ImageReader): ByteArray? {
        val image = reader.acquireLatestImage() ?: run {
            Log.e(TAG, "acquireLatestImage() retornou null")
            return null
        }

        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            Log.i(TAG, "Frame extraído: ${bytes.size / 1024} KB (${image.width}x${image.height})")
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair JPEG: ${e.message}", e)
            null
        } finally {
            image.close()
        }
    }

    /**
     * Processa e salva JPEG na galeria.
     *
     * @param jpegBytes    Bytes JPEG brutos do ImageReader
     * @param settings     Configurações de processamento (S-Curve + ColorMatrix)
     * @param onProgress   Callback de progresso (0.0..1.0)
     * @return Pair(uri, displayName) ou null se falhar
     */
    suspend fun processAndSave(
        jpegBytes: ByteArray,
        settings: ImageProcessor.Settings = ImageProcessor.Settings(),
        onProgress: ((Float) -> Unit)? = null
    ): Pair<Uri, String>? {
        return try {
            // Processa a imagem (Dispatchers.Default internamente)
            val processedBytes = processor.process(
                jpegBytes = jpegBytes,
                settings = settings,
                onProgress = { progress ->
                    // Processamento = 0..80% do total
                    onProgress?.invoke(progress * 0.8f)
                }
            )

            // Salva no MediaStore (IO)
            onProgress?.invoke(0.85f)
            val result = withContext(Dispatchers.IO) {
                saveJpegToGallery(processedBytes)
            }

            onProgress?.invoke(1.0f)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Erro no pipeline process+save: ${e.message}", e)
            null
        }
    }

    /**
     * Salva JPEG sem processar (fallback / modo direto).
     */
    fun saveRawJpeg(jpegBytes: ByteArray): Pair<Uri, String>? {
        return saveJpegToGallery(jpegBytes)
    }

    /**
     * Salva bytes JPEG na galeria via MediaStore.
     */
    private fun saveJpegToGallery(jpegBytes: ByteArray): Pair<Uri, String>? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val displayName = "${PHOTO_PREFIX}_$timestamp.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/OpticCore")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri == null) {
            Log.e(TAG, "MediaStore.insert retornou null")
            return null
        }

        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jpegBytes)
                outputStream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Log.i(TAG, "✅ Foto salva → $displayName ($uri)")
            Pair(uri, displayName)

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao escrever JPEG: ${e.message}", e)
            resolver.delete(uri, null, null)
            null
        }
    }
}

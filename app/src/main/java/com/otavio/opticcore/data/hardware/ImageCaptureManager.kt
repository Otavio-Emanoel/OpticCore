package com.otavio.opticcore.data.hardware

import android.content.ContentValues
import android.content.Context
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gerencia a persistência de imagens capturadas.
 * 
 * - Recebe frames do ImageReader
 * - Salva JPEG de alta qualidade no MediaStore
 * - Retorna URI e path da galeria
 *
 * Implementação do RF07 — Gravação em Disco (I/O).
 */
class ImageCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "OpticCore.Capture"
        private const val PHOTO_PREFIX = "OpticCore"
        private const val MIME_TYPE = "image/jpeg"
    }

    /**
     * Extrai o JPEG do ImageReader e salva no MediaStore.
     * @return Pair(uri, displayName) ou null se falhar.
     */
    fun saveImageFromReader(reader: ImageReader): Pair<Uri, String>? {
        val image = reader.acquireLatestImage() ?: run {
            Log.e(TAG, "acquireLatestImage() retornou null")
            return null
        }

        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            Log.i(TAG, "Frame capturado: ${bytes.size / 1024} KB (${image.width}x${image.height})")

            saveJpegToGallery(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar imagem: ${e.message}", e)
            null
        } finally {
            image.close()
        }
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

            // Marcar como não-pendente (disponível na galeria)
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

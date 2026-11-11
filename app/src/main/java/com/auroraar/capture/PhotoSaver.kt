package com.auroraar.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Still photos are the composited frame with no visible watermark on any plan
 * (v5 §6.4). The simulated-disclosure metadata is still written.
 */
object PhotoSaver {

    private const val TAG = "PhotoSaver"
    private const val MIME = "image/jpeg"
    private const val FOLDER = "AuroraAR"
    private const val DISCLOSURE = "Simulated aurora – made with Aurora AR"
    private val STAMP = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    fun save(context: Context, bitmap: Bitmap): Uri? = runCatching {
        val name = "AuroraAR_" + STAMP.format(Date()) + ".jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, bitmap, name)
        } else {
            saveLegacy(context, bitmap, name)
        }
    }.onFailure { Log.e(TAG, "Photo save failed", it) }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(context: Context, bitmap: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + FOLDER,
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.TITLE, DISCLOSURE)
            put(MediaStore.Images.Media.DESCRIPTION, DISCLOSURE)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            val ok = resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: false
            if (!ok) {
                resolver.delete(uri, null, null)
                return null
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            return uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun saveLegacy(context: Context, bitmap: Bitmap, name: String): Uri? {
        val dir = File(context.getExternalFilesDir(null), "Pictures/$FOLDER").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) return null
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(MIME), null)
        return Uri.fromFile(file)
    }
}

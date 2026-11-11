package com.auroraar.capture

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File

data class RecordedClip(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedMs: Long,
)

data class ShareTarget(val label: String, val packageName: String)

/**
 * Reads the app's own Movies/AuroraAR catalog through MediaStore on API 29+ and
 * from the app-specific folder on 26–28. No permission is needed because the
 * rows/files belong to this app.
 */
class RecordingsRepository(private val context: Context) {

    fun list(): List<RecordedClip> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listMediaStore() else listLegacy()
    }.onFailure { Log.w(TAG, "Could not list recordings", it) }.getOrDefault(emptyList())

    fun delete(clip: RecordedClip): Boolean = runCatching {
        if (clip.uri.scheme == "file") {
            File(clip.uri.path ?: return@runCatching false).delete()
        } else {
            context.contentResolver.delete(clip.uri, null, null) > 0
        }
    }.onFailure { Log.w(TAG, "Could not delete ${clip.uri}", it) }.getOrDefault(false)

    fun share(context: Context, clip: RecordedClip, targetPackage: String? = null) {
        val shareUri = if (clip.uri.scheme == "file") {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(clip.uri.path ?: error("missing path")),
            )
        } else {
            clip.uri
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_TEXT, context.getString(com.auroraar.R.string.record_share_caption))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            targetPackage?.let { setPackage(it) }
        }
        if (targetPackage != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(Intent.createChooser(intent, context.getString(com.auroraar.R.string.record_share)))
        }
    }

    /** Only targets that are actually installed are offered. */
    fun quickShareTargets(): List<ShareTarget> = QUICK_TARGETS.filter { target ->
        runCatching {
            context.packageManager.getPackageInfo(target.packageName, 0)
            true
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listMediaStore(): List<RecordedClip> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("Movies/$FOLDER%")
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val clips = mutableListOf<RecordedClip>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            sort,
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val date = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                clips += RecordedClip(
                    uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(id),
                    ),
                    name = cursor.getString(name) ?: "AuroraAR clip",
                    durationMs = cursor.getLong(duration),
                    sizeBytes = cursor.getLong(size),
                    dateAddedMs = cursor.getLong(date) * 1000L,
                )
            }
        }
        return clips
    }

    private fun listLegacy(): List<RecordedClip> {
        val dir = File(context.getExternalFilesDir(null), "Movies/$FOLDER")
        val files = dir.listFiles { file -> file.isFile && file.extension.equals("mp4", true) }
            ?: return emptyList()
        return files.map { file ->
            val retriever = MediaMetadataRetriever()
            val duration = runCatching {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            }.getOrDefault(0L)
            runCatching { retriever.release() }
            RecordedClip(
                uri = Uri.fromFile(file),
                name = file.name,
                durationMs = duration,
                sizeBytes = file.length(),
                dateAddedMs = file.lastModified(),
            )
        }.sortedByDescending { it.dateAddedMs }
    }

    private companion object {
        const val TAG = "RecordingsRepository"
        const val FOLDER = "AuroraAR"
        val QUICK_TARGETS = listOf(
            ShareTarget("WhatsApp", "com.whatsapp"),
            ShareTarget("Instagram", "com.instagram.android"),
            ShareTarget("YouTube", "com.google.android.youtube"),
        )
    }
}

package com.auroraar.capture

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Process death can leave MediaStore rows with IS_PENDING=1 (unplayable) or
 * legacy .tmp files in the app folder. The next launch removes only rows this
 * app owns and that are older than the grace period, so an in-flight recording
 * from another process is never touched.
 */
object PendingRecordingCleanup {

    private const val TAG = "PendingCleanup"
    const val STALE_MS = 60_000L

    fun isStale(dateAddedMs: Long, nowMs: Long): Boolean =
        dateAddedMs > 0L && nowMs - dateAddedMs > STALE_MS

    fun clean(context: Context) {
        val now = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cleanPending(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, now)
            cleanPending(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, now)
        }
        cleanLegacyTempFiles(context, now)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun cleanPending(context: Context, collection: Uri, nowMs: Long) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val selection = "${MediaStore.MediaColumns.IS_PENDING}=1 AND " +
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=?"
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                arrayOf(context.packageName),
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val dateAddedMs = cursor.getLong(dateColumn) * 1000L
                    if (!isStale(dateAddedMs, nowMs)) continue
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                    val deleted = runCatching { context.contentResolver.delete(uri, null, null) }
                        .getOrDefault(0)
                    if (deleted > 0) Log.i(TAG, "Removed orphaned pending row $uri")
                }
            }
        }.onFailure { Log.w(TAG, "Pending cleanup failed for $collection", it) }
    }

    private fun cleanLegacyTempFiles(context: Context, nowMs: Long) {
        val dir = File(context.getExternalFilesDir(null), "Movies/AuroraAR")
        val files = dir.listFiles() ?: return
        files.forEach { file ->
            if (!file.isFile) return@forEach
            val stale = nowMs - file.lastModified() > STALE_MS
            val temporary = file.name.endsWith(".tmp", ignoreCase = true) ||
                file.name.endsWith(".mp4.tmp", ignoreCase = true)
            if (stale && (temporary || file.length() == 0L)) {
                if (file.delete()) Log.i(TAG, "Removed legacy orphan ${file.name}")
            }
        }
    }
}

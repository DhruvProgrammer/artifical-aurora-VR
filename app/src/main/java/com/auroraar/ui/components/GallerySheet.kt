package com.auroraar.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.auroraar.R
import com.auroraar.capture.RecordedClip
import com.auroraar.capture.RecordingsRepository
import com.auroraar.capture.ShareTarget
import com.auroraar.capture.formatDuration
import com.auroraar.ui.theme.AuroraGreen
import com.auroraar.ui.theme.NightSurface
import com.auroraar.ui.theme.NightSurfaceHigh
import com.auroraar.ui.theme.TextMuted
import com.auroraar.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app clip gallery. Lists only this app's `Movies/AuroraAR` output through
 * MediaStore (or the app folder on API 26–28), so no storage permission is
 * needed. Tapping a clip opens a player with share, quick-share and delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { RecordingsRepository(context) }
    var clips by remember { mutableStateOf<List<RecordedClip>?>(null) }
    var shareTargets by remember { mutableStateOf<List<ShareTarget>>(emptyList()) }
    var preview by remember { mutableStateOf<RecordedClip?>(null) }
    var pendingDelete by remember { mutableStateOf<RecordedClip?>(null) }

    LaunchedEffect(Unit) {
        clips = withContext(Dispatchers.IO) { repository.list() }
        shareTargets = withContext(Dispatchers.IO) { repository.quickShareTargets() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NightSurface,
        contentColor = TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.gallery_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.gallery_saved_path),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                clips?.let {
                    Text(
                        text = pluralStringResource(R.plurals.gallery_count, it.size, it.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.gallery_close),
                        tint = TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val list = clips
            when {
                list == null -> Text(
                    text = stringResource(R.string.gallery_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                list.isEmpty() -> Text(
                    text = stringResource(R.string.gallery_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.uri.toString() }) { clip ->
                        GalleryRow(
                            clip = clip,
                            onOpen = { preview = clip },
                            onShare = {
                                runCatching { repository.share(context, clip) }
                                    .onFailure {
                                        Toast.makeText(context, R.string.gallery_share_failed, Toast.LENGTH_LONG).show()
                                    }
                            },
                            onDelete = { pendingDelete = clip },
                        )
                    }
                }
            }
        }
    }

    preview?.let { clip ->
        ClipPreviewDialog(
            clip = clip,
            targets = shareTargets,
            onShare = { target ->
                runCatching { repository.share(context, clip, target.packageName) }
                    .onFailure {
                        Toast.makeText(context, R.string.gallery_share_failed, Toast.LENGTH_LONG).show()
                    }
            },
            onShareGeneric = {
                runCatching { repository.share(context, clip) }
                    .onFailure {
                        Toast.makeText(context, R.string.gallery_share_failed, Toast.LENGTH_LONG).show()
                    }
            },
            onDelete = {
                preview = null
                pendingDelete = clip
            },
            onDismiss = { preview = null },
        )
    }

    pendingDelete?.let { clip ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = NightSurfaceHigh,
            title = { Text(stringResource(R.string.gallery_delete_title)) },
            text = { Text(stringResource(R.string.gallery_delete_body), color = TextMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = repository.delete(clip)
                        if (deleted) {
                            clips = clips?.filterNot { it.uri == clip.uri }
                        } else {
                            Toast.makeText(context, R.string.gallery_delete_failed, Toast.LENGTH_LONG).show()
                        }
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.record_delete), color = Color(0xFFFF8A80))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.record_close), color = TextMuted)
                }
            },
        )
    }
}

@Composable
private fun GalleryRow(
    clip: RecordedClip,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onOpen)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClipThumbnail(
            uri = clip.uri,
            modifier = Modifier
                .size(width = 96.dp, height = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = dateLabel(clip.dateAddedMs),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatDuration(clip.durationMs)} · ${sizeLabel(clip.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        IconButton(onClick = onShare) {
            Icon(
                Icons.Filled.Share,
                contentDescription = stringResource(R.string.record_share),
                tint = TextMuted,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.record_delete),
                tint = Color(0xFFFF8A80),
            )
        }
    }
}

@Composable
private fun ClipThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var frame by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        frame = withContext(Dispatchers.IO) { loadThumbnail(context, uri) }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bitmap = frame
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipPreviewDialog(
    clip: RecordedClip,
    targets: List<ShareTarget>,
    onShare: (ShareTarget) -> Unit,
    onShareGeneric: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val videoView = remember(clip.uri) {
            VideoView(context).apply {
                setVideoURI(clip.uri)
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
            }
        }
        DisposableEffect(videoView) {
            onDispose { videoView.stopPlayback() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NightSurface, RoundedCornerShape(22.dp))
                .padding(16.dp),
        ) {
            Text(
                text = dateLabel(clip.dateAddedMs),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            AndroidView(
                factory = { videoView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            )
            if (targets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.gallery_share_to),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    targets.forEach { target ->
                        TextButton(onClick = { onShare(target) }) {
                            Text(target.label, color = AuroraGreen)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onShareGeneric,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuroraGreen,
                        contentColor = Color(0xFF04231A),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.record_share))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.record_delete), color = Color(0xFFFF8A80))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.record_close), color = TextMuted)
                }
            }
        }
    }
}

private fun loadThumbnail(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        if (uri.scheme == "file") {
            retriever.setDataSource(uri.path ?: return null)
        } else {
            retriever.setDataSource(context, uri)
        }
        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        if (frame == null || frame.width <= 320) {
            frame
        } else {
            val height = (320f * frame.height / frame.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(frame, 320, height, true)
        }
    } catch (t: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun dateLabel(epochMs: Long): String =
    SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun sizeLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}

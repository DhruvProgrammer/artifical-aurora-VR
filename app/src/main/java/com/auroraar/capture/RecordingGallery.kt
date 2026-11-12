package com.auroraar.capture

import android.provider.MediaStore

/**
 * The MediaStore filter that selects this app's own recordings.
 *
 * Kept as pure strings so the query contract can be asserted on the JVM: this
 * project has no Robolectric, so a ContentResolver cannot be exercised in a unit
 * test, but the selection and its arguments can still be checked.
 *
 * The match is **exact**, not a prefix. `RELATIVE_PATH LIKE 'Movies/AuroraAR%'`
 * also selects `Movies/AuroraArchive/` and `Movies/AuroraAR2/`, which belong to
 * other apps; those rows would appear in the gallery and cannot be deleted here.
 */
object RecordingGallery {

    /** Which public folder the recorder writes into. */
    fun relativePath(): String = "Movies/${RecordingsRepository.FOLDER}"

    val SELECTION: String = "${MediaStore.Video.Media.RELATIVE_PATH} IN (?, ?)"

    /**
     * MediaStore stores `RELATIVE_PATH` with a trailing slash for files, but the
     * value is not guaranteed to be normalised on every OEM or for legacy rows,
     * so both spellings are matched explicitly.
     */
    fun args(): Array<String> = arrayOf(
        relativePath() + "/",
        relativePath(),
    )
}

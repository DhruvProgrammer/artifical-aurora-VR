package com.auroraar.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gallery query must select **only** this app's own folder.
 *
 * `RELATIVE_PATH LIKE 'Movies/AuroraAR%'` is a prefix match, so it also picks up
 * any sibling folder that happens to start with the same characters
 * (`Movies/AuroraArchive/`, `Movies/AuroraAR2/`). Those rows belong to another
 * app, and listing them means the gallery offers videos this app cannot delete
 * and did not create.
 *
 * There is no Robolectric in this project, so the selection cannot be exercised
 * through a real ContentResolver. Instead the test evaluates the published
 * selection/args pair with a small SQL matcher, which is exactly the contract
 * MediaStore is given.
 */
class RecordingGalleryQueryTest {

    /** Interprets the two selection operators this query is allowed to use. */
    private fun apply(paths: List<String>, selection: String, args: Array<String>): List<String> {
        val argIndex = selection.split("?").size - 2
        return when {
            selection.endsWith("LIKE ?") && args.size == 1 -> {
                val prefix = args[0].removeSuffix("%")
                paths.filter { it.startsWith(prefix) }
            }
            selection.contains(" IN ") && args.isNotEmpty() -> paths.filter { it in args }
            selection.endsWith("= ?") && args.size == 1 -> paths.filter { it == args[0] }
            else -> error("unsupported selection shape: '$selection' args=${args.toList()} argSlots=$argIndex")
        }
    }

    private val ours = listOf(
        "Movies/AuroraAR/AuroraAR_20261112_151122_441.mp4",
        "Movies/AuroraAR/",
    )

    @Test
    fun `our own clips are selected`() {
        val selected = apply(
            ours,
            RecordingGallery.SELECTION,
            RecordingGallery.args(),
        )
        assertTrue(
            "our own folder must be selected, got $selected",
            "Movies/AuroraAR/" in selected,
        )
    }

    @Test
    fun `a sibling folder sharing our prefix is not selected`() {
        // Real-world example: another app writes to Movies/AuroraArchive/.
        val foreign = "Movies/AuroraArchive/private_clip.mp4"
        val selected = apply(
            listOf(foreign) + ours,
            RecordingGallery.SELECTION,
            RecordingGallery.args(),
        )
        assertTrue(
            "a prefix match would list another app's video; selection='${RecordingGallery.SELECTION}' " +
                "args=${RecordingGallery.args().toList()} selected=$selected",
            foreign !in selected,
        )
    }

    @Test
    fun `a folder that only extends our name is not selected`() {
        val foreign = "Movies/AuroraAR2/clip.mp4"
        val selected = apply(
            listOf(foreign),
            RecordingGallery.SELECTION,
            RecordingGallery.args(),
        )
        assertEquals(emptyList<String>(), selected)
    }

    @Test
    fun `the query does not use a prefix wildcard`() {
        // The wildcard is the bug: it turns an exact folder match into a prefix.
        assertTrue(
            "selection must not be a prefix LIKE: '${RecordingGallery.SELECTION}'",
            !RecordingGallery.SELECTION.contains("LIKE"),
        )
        assertTrue(
            "no argument may end with a wildcard: ${RecordingGallery.args().toList()}",
            RecordingGallery.args().none { it.endsWith("%") || it.contains("%") },
        )
    }

    @Test
    fun `relative path is built from the public Movies directory`() {
        assertEquals("Movies/AuroraAR", RecordingGallery.relativePath())
    }
}

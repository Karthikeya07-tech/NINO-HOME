package com.example.nino_home

/**
 * Media shown under My Music → Media, and available when attaching audio to an action.
 */
enum class MediaKind {
    Demo,
}

data class MediaItem(
    val id: String,
    val name: String,
    val description: String,
    /** Length in ms when known; null if the bot script length is not catalogued yet. */
    val durationMs: Int?,
    val kind: MediaKind,
)

object MediaLibrary {
    const val DEMO_ID = "media_demo"

    fun all(): List<MediaItem> = listOf(
        MediaItem(
            id = DEMO_ID,
            name = "Demo",
            description = "Built-in firmware script",
            // Approximate; replace when the bot reports real media metadata.
            durationMs = 30_000,
            kind = MediaKind.Demo,
        ),
    )

    fun findById(id: String?): MediaItem? =
        id?.let { wanted -> all().find { it.id == wanted } }
}

fun formatDurationSeconds(ms: Int?): String {
    if (ms == null || ms < 0) return "—"
    val seconds = ms / 1000.0
    return if (seconds == seconds.toLong().toDouble()) {
        "${seconds.toLong()} s"
    } else {
        "%.1f s".format(seconds)
    }
}

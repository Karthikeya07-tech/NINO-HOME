package com.example.nino_home

/**
 * Media shown under My Music → Media, and available when attaching audio to an action.
 */
enum class MediaKind {
    Demo,
    Local,
}

data class MediaItem(
    val id: String,
    val name: String,
    val description: String,
    /** Length in ms when known; null if the bot script length is not catalogued yet. */
    val durationMs: Int?,
    val kind: MediaKind,
    /** Content URI for [MediaKind.Local] files picked from the phone. */
    val uri: String? = null,
)

object MediaLibrary {
    const val DEMO_ID = "media_demo"

    fun builtIn(): List<MediaItem> = listOf(
        MediaItem(
            id = DEMO_ID,
            name = "Demo",
            description = "Built-in firmware script",
            durationMs = 30_000,
            kind = MediaKind.Demo,
        ),
    )

    fun all(local: List<MediaItem> = emptyList()): List<MediaItem> = builtIn() + local

    fun findById(id: String?, local: List<MediaItem> = emptyList()): MediaItem? =
        id?.let { wanted -> all(local).find { it.id == wanted } }
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

fun formatPlaybackClock(ms: Int?): String {
    if (ms == null || ms < 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

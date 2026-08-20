package com.example.nino_home

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nino_home.audio.PlayWavClient
import com.example.nino_home.audio.PlayWavFeed
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class MusicPlaybackStatus {
    Idle,
    Preparing,
    Playing,
    Paused,
    Stopped,
}

data class MusicPlayerUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val title: String = "No song",
    val status: MusicPlaybackStatus = MusicPlaybackStatus.Idle,
    val statusLabel: String = "Stopped",
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val error: String? = null,
)

class MusicStreamViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MusicStreamVM"
    }

    private val localMedia = LocalMediaRepository(application)
    private val _uiState = MutableStateFlow(MusicPlayerUiState(mediaItems = catalog()))
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

    private var bot: BotService? = null
    private var demoJob: Job? = null
    private var cachedItem: MediaItem? = null
    private var transportJob: Job? = null

    init {
        viewModelScope.launch {
            MusicStreamService.state.collect { serviceState ->
                val localSession = cachedItem?.kind == MediaKind.Local
                if (!localSession && !serviceState.active) return@collect
                if (serviceState.active ||
                    serviceState.status == MusicPlaybackStatus.Playing ||
                    serviceState.status == MusicPlaybackStatus.Preparing ||
                    serviceState.status == MusicPlaybackStatus.Paused ||
                    serviceState.status == MusicPlaybackStatus.Stopped
                ) {
                    _uiState.update {
                        it.copy(
                            title = serviceState.title.ifBlank { it.title },
                            status = serviceState.status,
                            statusLabel = serviceState.statusLabel,
                            isPlaying = serviceState.isPlaying,
                            positionMs = serviceState.positionMs,
                            durationMs = serviceState.durationMs.takeIf { d -> d > 0 }
                                ?: it.durationMs,
                            error = serviceState.error,
                        )
                    }
                }
            }
        }
    }

    fun bindBot(bot: BotService?) {
        this.bot = bot
        refreshLibrary()
    }

    fun refreshLibrary() {
        _uiState.update { it.copy(mediaItems = catalog()) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun importFromUri(uri: Uri, playNow: Boolean = true) {
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val item = runCatching { describeLocalFile(uri) }.getOrElse { err ->
                _uiState.update {
                    it.copy(error = err.message ?: "Could not open that audio file")
                }
                return@launch
            }
            localMedia.upsert(item)
            _uiState.update { it.copy(mediaItems = catalog(), error = null) }
            if (playNow) {
                playMedia(item)
            }
        }
    }

    fun playMedia(item: MediaItem) {
        val target = bot
        if (target == null) {
            _uiState.update { it.copy(error = "No device selected. Keep the bot on the same Wi-Fi.") }
            return
        }
        when (item.kind) {
            MediaKind.Demo -> playDemo(target, item)
            MediaKind.Local -> {
                demoJob?.cancel()
                cachedItem = item
                MusicStreamService.play(
                    context = getApplication(),
                    bot = target,
                    item = item,
                    reset = true,
                )
            }
        }
    }

    fun togglePlayPause() {
        val state = _uiState.value
        when {
            state.isPlaying && cachedItem?.kind == MediaKind.Local -> pauseLocalImmediate()
            state.status == MusicPlaybackStatus.Paused && cachedItem?.kind == MediaKind.Local ->
                resumeLocalImmediate()
            state.isPlaying -> {
                // Demo or unknown: stop local timer.
                demoJob?.cancel()
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        status = MusicPlaybackStatus.Paused,
                        statusLabel = "Paused",
                    )
                }
            }
            else -> {
                val item = cachedItem ?: state.mediaItems.firstOrNull { it.id == MediaLibrary.DEMO_ID }
                if (item == null) {
                    _uiState.update { it.copy(error = "Pick a song from Media or from your phone.") }
                    return
                }
                val target = bot
                if (target == null) {
                    _uiState.update { it.copy(error = "No device selected.") }
                    return
                }
                when (item.kind) {
                    MediaKind.Demo -> playDemo(target, item)
                    MediaKind.Local -> {
                        if (state.status == MusicPlaybackStatus.Paused) {
                            resumeLocalImmediate()
                        } else {
                            MusicStreamService.play(
                                context = getApplication(),
                                bot = target,
                                item = item,
                                reset = true,
                            )
                        }
                    }
                }
            }
        }
    }

    fun pause() {
        if (cachedItem?.kind == MediaKind.Local) {
            pauseLocalImmediate()
            return
        }
        demoJob?.cancel()
        _uiState.update {
            it.copy(
                isPlaying = false,
                status = MusicPlaybackStatus.Paused,
                statusLabel = "Paused",
            )
        }
    }

    fun stop() {
        if (cachedItem?.kind == MediaKind.Local) {
            stopLocalImmediate()
            return
        }
        demoJob?.cancel()
        _uiState.update {
            it.copy(
                isPlaying = false,
                status = MusicPlaybackStatus.Stopped,
                statusLabel = "Stopped",
                positionMs = 0,
            )
        }
    }

    /**
     * Firmware owns pause: stop POSTs and hit `/play_wav/pause` on IO
     * immediately (do not wait for the service main-thread handler).
     */
    private fun pauseLocalImmediate() {
        val target = bot
        PlayWavFeed.pauseFeeding()
        _uiState.update {
            it.copy(
                isPlaying = false,
                status = MusicPlaybackStatus.Paused,
                statusLabel = "Paused",
            )
        }
        transportJob?.cancel()
        transportJob = viewModelScope.launch(Dispatchers.IO) {
            if (target != null) {
                runCatching { PlayWavClient.pause(target) }
                    .onFailure { err ->
                        Log.e(TAG, "play_wav/pause failed", err)
                        _uiState.update {
                            it.copy(error = err.message ?: "Pause failed on device")
                        }
                    }
            } else {
                Log.e(TAG, "pause: no bot selected")
            }
            // Keep service notification / feed state in sync.
            MusicStreamService.pause(getApplication())
        }
    }

    private fun resumeLocalImmediate() {
        val target = bot
        _uiState.update {
            it.copy(
                isPlaying = true,
                status = MusicPlaybackStatus.Playing,
                statusLabel = "Playing on device",
            )
        }
        transportJob?.cancel()
        transportJob = viewModelScope.launch(Dispatchers.IO) {
            if (target != null) {
                runCatching { PlayWavClient.resume(target) }
                    .onFailure { err ->
                        Log.e(TAG, "play_wav/resume failed", err)
                        _uiState.update {
                            it.copy(error = err.message ?: "Resume failed on device")
                        }
                    }
            }
            PlayWavFeed.resumeFeeding()
            MusicStreamService.resume(getApplication())
        }
    }

    private fun stopLocalImmediate() {
        val target = bot
        PlayWavFeed.cancel()
        PlayWavFeed.resetSliceStart()
        _uiState.update {
            it.copy(
                isPlaying = false,
                status = MusicPlaybackStatus.Stopped,
                statusLabel = "Stopped",
                positionMs = 0,
            )
        }
        transportJob?.cancel()
        transportJob = viewModelScope.launch(Dispatchers.IO) {
            if (target != null) {
                runCatching { PlayWavClient.stop(target) }
                    .onFailure { err -> Log.e(TAG, "play_wav/stop failed", err) }
            }
            MusicStreamService.stop(getApplication())
        }
    }

    override fun onCleared() {
        // Do not stop the foreground stream when leaving the screen —
        // wifi stream.md requires POSTs to continue in the background.
        demoJob?.cancel()
        super.onCleared()
    }

    private fun catalog(): List<MediaItem> = MediaLibrary.all(localMedia.load())

    private fun playDemo(bot: BotService, item: MediaItem) {
        MusicStreamService.stop(getApplication())
        demoJob?.cancel()
        cachedItem = item
        val duration = item.durationMs ?: 30_000
        demoJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    title = item.name,
                    status = MusicPlaybackStatus.Playing,
                    statusLabel = "Playing",
                    isPlaying = true,
                    positionMs = 0,
                    durationMs = duration,
                    error = null,
                )
            }
            val result = runCatching { postDemo(bot) }
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        status = MusicPlaybackStatus.Stopped,
                        statusLabel = "Stopped",
                        error = result.exceptionOrNull()?.message ?: "Demo play failed",
                    )
                }
                return@launch
            }
            val started = System.currentTimeMillis()
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - started).toInt().coerceAtMost(duration)
                _uiState.update { it.copy(positionMs = elapsed) }
                if (elapsed >= duration) break
                delay(200)
            }
            if (isActive) {
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        status = MusicPlaybackStatus.Stopped,
                        statusLabel = "Finished",
                        positionMs = duration,
                    )
                }
            }
        }
    }

    private fun describeLocalFile(uri: Uri): MediaItem {
        val app = getApplication<Application>()
        var name = "Song"
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(index)?.substringBeforeLast('.') ?: name
            }
        }
        var durationMs: Int? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(app, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toIntOrNull()
        } finally {
            retriever.release()
        }
        return MediaItem(
            id = LocalMediaRepository.idForUri(uri),
            name = name,
            description = "From phone",
            durationMs = durationMs,
            kind = MediaKind.Local,
            uri = uri.toString(),
        )
    }

    private fun postDemo(bot: BotService) {
        val url = URL("http://${bot.host}:${bot.port}/demo")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val body = JSONObject().put("play", true).toString()
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("Demo play request failed ($code)")
            }
        } finally {
            conn.disconnect()
        }
    }
}

package com.example.nino_home

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nino_home.audio.PlayWavFeed
import com.example.nino_home.audio.StreamingPcmDecoder
import com.example.nino_home.audio.WavPcm
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException
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
    private val localMedia = LocalMediaRepository(application)
    private val _uiState = MutableStateFlow(MusicPlayerUiState(mediaItems = catalog()))
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

    private var bot: BotService? = null
    private var feedJob: Job? = null
    private var cachedItem: MediaItem? = null
    private var cachedPcm: ByteArray? = null
    private var playheadBytes: Int = 0

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
            MediaKind.Local -> playLocal(target, item, resume = false)
        }
    }

    fun togglePlayPause() {
        val state = _uiState.value
        if (state.isPlaying) {
            pause()
            return
        }
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
            MediaKind.Local -> playLocal(target, item, resume = playheadBytes > 0)
        }
    }

    fun pause() {
        PlayWavFeed.cancel()
        feedJob?.cancel()
        feedJob = null
        val position = WavPcm.durationMsForPcmBytes(playheadBytes)
        _uiState.update {
            it.copy(
                isPlaying = false,
                status = MusicPlaybackStatus.Paused,
                statusLabel = "Paused",
                positionMs = position.coerceAtMost(it.durationMs),
            )
        }
    }

    fun stop() {
        PlayWavFeed.cancel()
        feedJob?.cancel()
        feedJob = null
        playheadBytes = 0
        _uiState.update {
            it.copy(
                isPlaying = false,
                status = MusicPlaybackStatus.Stopped,
                statusLabel = "Stopped",
                positionMs = 0,
            )
        }
    }

    override fun onCleared() {
        PlayWavFeed.cancel()
        feedJob?.cancel()
        super.onCleared()
    }

    private fun catalog(): List<MediaItem> = MediaLibrary.all(localMedia.load())

    private fun playDemo(bot: BotService, item: MediaItem) {
        PlayWavFeed.cancel()
        feedJob?.cancel()
        cachedItem = item
        cachedPcm = null
        playheadBytes = 0
        val duration = item.durationMs ?: 30_000
        feedJob = viewModelScope.launch(Dispatchers.IO) {
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

    private fun playLocal(bot: BotService, item: MediaItem, resume: Boolean) {
        val uri = item.uri?.let(Uri::parse)
        if (uri == null) {
            _uiState.update { it.copy(error = "That song is missing a file path.") }
            return
        }
        PlayWavFeed.cancel()
        feedJob?.cancel()
        cachedItem = item
        if (!resume) {
            playheadBytes = 0
            cachedPcm = null
        }
        feedJob = viewModelScope.launch(Dispatchers.IO) {
            val durationHint = item.durationMs ?: 0
            _uiState.update {
                it.copy(
                    title = item.name,
                    status = MusicPlaybackStatus.Preparing,
                    statusLabel = "Starting…",
                    isPlaying = true,
                    positionMs = WavPcm.durationMsForPcmBytes(playheadBytes),
                    durationMs = durationHint,
                    error = null,
                )
            }
            try {
                val cached = cachedPcm
                val completed = if (cached != null && cachedItem?.id == item.id) {
                    val durationMs = WavPcm.durationMsForPcmBytes(cached.size)
                    _uiState.update {
                        it.copy(
                            status = MusicPlaybackStatus.Playing,
                            statusLabel = "Playing on device",
                            durationMs = durationMs,
                        )
                    }
                    PlayWavFeed.stream(
                        bot = bot,
                        pcm = cached,
                        startPcmOffset = playheadBytes,
                        onPlayhead = { offset ->
                            playheadBytes = offset
                            _uiState.update { state ->
                                state.copy(
                                    positionMs = WavPcm.durationMsForPcmBytes(offset)
                                        .coerceAtMost(durationMs),
                                )
                            }
                        },
                    )
                } else {
                    StreamingPcmDecoder(getApplication(), uri).use { decoder ->
                        val durationMs = decoder.durationMs.takeIf { it > 0 } ?: durationHint
                        _uiState.update {
                            it.copy(
                                status = MusicPlaybackStatus.Playing,
                                statusLabel = "Playing on device",
                                durationMs = durationMs,
                            )
                        }
                        PlayWavFeed.streamLive(
                            bot = bot,
                            decoder = decoder,
                            startPcmOffset = playheadBytes,
                            onPlayhead = { offset ->
                                playheadBytes = offset
                                _uiState.update { state ->
                                    state.copy(
                                        positionMs = WavPcm.durationMsForPcmBytes(offset)
                                            .coerceAtMost(durationMs.coerceAtLeast(1)),
                                    )
                                }
                            },
                        )
                    }
                }
                if (!isActive) return@launch
                if (completed) {
                    val endMs = _uiState.value.durationMs
                    playheadBytes = 0
                    _uiState.update {
                        it.copy(
                            isPlaying = false,
                            status = MusicPlaybackStatus.Stopped,
                            statusLabel = "Finished",
                            positionMs = endMs,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        status = MusicPlaybackStatus.Paused,
                        statusLabel = "Paused",
                        error = e.message ?: "Failed to send audio to the device",
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

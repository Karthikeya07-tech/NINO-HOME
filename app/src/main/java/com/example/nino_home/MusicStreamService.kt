package com.example.nino_home

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.nino_home.audio.PlayWavClient
import com.example.nino_home.audio.PlayWavFeed
import com.example.nino_home.audio.StreamingPcmDecoder
import com.example.nino_home.audio.WavPcm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps `/play_wav` chunk POSTs alive while the UI is backgrounded
 * (wifi stream.md §5).
 */
class MusicStreamService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var feedJob: Job? = null
    private val playGeneration = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, 80)
                val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Song"
                val durationMs = intent.getIntExtra(EXTRA_DURATION_MS, 0)
                val reset = intent.getBooleanExtra(EXTRA_RESET, true)
                val seekMs = intent.getIntExtra(EXTRA_SEEK_MS, -1)
                startPlayback(
                    bot = BotService(
                        serviceName = title,
                        serviceType = "_nino._tcp.",
                        host = host,
                        hostName = null,
                        port = port,
                    ),
                    uri = uri,
                    title = title,
                    durationMs = durationMs,
                    reset = reset,
                    seekMs = seekMs.takeIf { it >= 0 },
                )
            }
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_STOP -> stopPlayback(userStop = true)
            else -> Unit
        }
        return START_STICKY
    }

    override fun onDestroy() {
        playGeneration.incrementAndGet()
        feedJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startPlayback(
        bot: BotService,
        uri: Uri,
        title: String,
        durationMs: Int,
        reset: Boolean,
        seekMs: Int? = null,
    ) {
        // Bump generation so any cancelled prior feed cannot update UI/errors.
        val generation = playGeneration.incrementAndGet()
        PlayWavFeed.cancel()
        feedJob?.cancel()
        feedJob = null

        val startMs = when {
            seekMs != null -> seekMs.coerceIn(0, durationMs.coerceAtLeast(0))
            reset -> 0
            else -> _state.value.positionMs
        }

        promoteForeground(title, if (seekMs != null) "Seeking…" else "Starting…", playing = true)
        activeBot = bot
        _state.update {
            it.copy(
                uri = uri.toString(),
                title = title,
                status = MusicPlaybackStatus.Preparing,
                statusLabel = if (seekMs != null) "Seeking…" else "Starting…",
                isPlaying = true,
                positionMs = startMs,
                durationMs = durationMs.takeIf { d -> d > 0 } ?: it.durationMs,
                error = null,
                active = true,
            )
        }

        // Stop robot queue, then stream from the chosen offset (seek is app-owned).
        feedJob = scope.launch {
            runCatching { PlayWavClient.stop(bot) }
                .onFailure { Log.w("MusicStreamService", "stop before play/seek failed", it) }
            if (playGeneration.get() != generation) return@launch

            val pcmStart = WavPcm.pcmBytesForDurationMs(startMs) and 1.inv()
            PlayWavFeed.setNextSliceStart(pcmStart)

            beginFeed(
                bot = bot,
                uri = uri,
                title = title,
                durationMs = durationMs,
                startPcmOffset = pcmStart,
                generation = generation,
            )
        }
    }

    private suspend fun beginFeed(
        bot: BotService,
        uri: Uri,
        title: String,
        durationMs: Int,
        startPcmOffset: Int,
        generation: Int,
    ) {
        if (playGeneration.get() != generation) return
        try {
            StreamingPcmDecoder(applicationContext, uri).use { decoder ->
                if (playGeneration.get() != generation) return
                val totalMs = decoder.durationMs.takeIf { it > 0 } ?: durationMs
                _state.update {
                    it.copy(
                        title = title,
                        status = MusicPlaybackStatus.Playing,
                        statusLabel = "Playing on device",
                        durationMs = totalMs,
                        positionMs = WavPcm.durationMsForPcmBytes(startPcmOffset)
                            .coerceAtMost(totalMs.coerceAtLeast(1)),
                        isPlaying = true,
                        error = null,
                        active = true,
                    )
                }
                updateNotification(title, "Playing on device", playing = true)

                val completed = PlayWavFeed.streamLive(
                    bot = bot,
                    decoder = decoder,
                    startPcmOffset = startPcmOffset,
                    onPlayhead = { offset ->
                        if (playGeneration.get() != generation) return@streamLive
                        _state.update { state ->
                            state.copy(
                                positionMs = WavPcm.durationMsForPcmBytes(offset)
                                    .coerceAtMost(totalMs.coerceAtLeast(1)),
                            )
                        }
                    },
                )
                if (playGeneration.get() != generation) return
                if (completed) {
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            status = MusicPlaybackStatus.Stopped,
                            statusLabel = "Finished",
                            positionMs = it.durationMs,
                            active = false,
                            error = null,
                        )
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } catch (_: CancellationException) {
            // Expected when switching songs, seeking, or stopping.
        } catch (e: Exception) {
            if (playGeneration.get() != generation) return
            Log.e("MusicStreamService", "feed failed", e)
            _state.update {
                it.copy(
                    isPlaying = false,
                    status = MusicPlaybackStatus.Paused,
                    statusLabel = "Paused",
                    error = e.message ?: "Failed to send audio to the device",
                    active = true,
                )
            }
            updateNotification(title, "Paused", playing = false)
        }
    }

    private fun pausePlayback() {
        val bot = activeBot
        PlayWavFeed.pauseFeeding()
        _state.update {
            it.copy(
                isPlaying = false,
                status = MusicPlaybackStatus.Paused,
                statusLabel = "Paused",
                active = true,
            )
        }
        updateNotification(_state.value.title, "Paused", playing = false)
        if (bot != null) {
            scope.launch {
                runCatching { PlayWavClient.pause(bot) }
                    .onFailure { Log.e("MusicStreamService", "pause failed", it) }
            }
        }
    }

    private fun resumePlayback() {
        val bot = activeBot
        val current = _state.value
        if (bot != null) {
            scope.launch {
                runCatching { PlayWavClient.resume(bot) }
                    .onFailure { Log.e("MusicStreamService", "resume failed", it) }
                PlayWavFeed.resumeFeeding()
            }
        } else {
            PlayWavFeed.resumeFeeding()
        }
        _state.update {
            it.copy(
                isPlaying = true,
                status = MusicPlaybackStatus.Playing,
                statusLabel = "Playing on device",
                active = true,
            )
        }
        updateNotification(current.title, "Playing on device", playing = true)

        if (feedJob?.isActive != true && bot != null) {
            val uri = current.uri ?: return
            promoteForeground(current.title, "Playing on device", playing = true)
            val generation = playGeneration.incrementAndGet()
            feedJob = scope.launch {
                beginFeed(
                    bot = bot,
                    uri = Uri.parse(uri),
                    title = current.title,
                    durationMs = current.durationMs,
                    startPcmOffset = PlayWavFeed.nextSliceStart,
                    generation = generation,
                )
            }
        }
    }

    private fun stopPlayback(userStop: Boolean) {
        playGeneration.incrementAndGet()
        PlayWavFeed.cancel()
        PlayWavFeed.resetSliceStart()
        feedJob?.cancel()
        feedJob = null
        // HTTP stop is issued by ViewModel; best-effort here if service-only stop.
        val bot = activeBot
        if (bot != null) {
            scope.launch {
                runCatching { PlayWavClient.stop(bot) }
            }
        }
        activeBot = null
        _state.update {
            it.copy(
                isPlaying = false,
                status = MusicPlaybackStatus.Stopped,
                statusLabel = if (userStop) "Stopped" else it.statusLabel,
                positionMs = if (userStop) 0 else it.positionMs,
                active = false,
                error = null,
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promoteForeground(title: String, text: String, playing: Boolean) {
        ensureChannel()
        val notification = buildNotification(title, text, playing)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nino music",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps streaming audio to your Nino while the app is in the background"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String, playing: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseResume = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicStreamService::class.java).setAction(
                if (playing) ACTION_PAUSE else ACTION_RESUME,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicStreamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_speaker)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                if (playing) "Pause" else "Resume",
                pauseResume,
            )
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun updateNotification(title: String, text: String, playing: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, text, playing))
    }

    companion object {
        const val ACTION_PLAY = "com.example.nino_home.action.PLAY"
        const val ACTION_PAUSE = "com.example.nino_home.action.PAUSE"
        const val ACTION_RESUME = "com.example.nino_home.action.RESUME"
        const val ACTION_STOP = "com.example.nino_home.action.STOP"

        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DURATION_MS = "durationMs"
        const val EXTRA_RESET = "reset"
        const val EXTRA_SEEK_MS = "seekMs"

        private const val CHANNEL_ID = "nino_music_stream"
        private const val NOTIFICATION_ID = 42

        private val _state = MutableStateFlow(MusicStreamServiceState())
        val state: StateFlow<MusicStreamServiceState> = _state.asStateFlow()

        @Volatile
        private var activeBot: BotService? = null

        fun play(
            context: Context,
            bot: BotService,
            item: MediaItem,
            reset: Boolean = true,
            seekMs: Int? = null,
        ) {
            val uri = item.uri ?: return
            val startMs = seekMs?.coerceAtLeast(0) ?: if (reset) 0 else _state.value.positionMs
            _state.update {
                it.copy(
                    uri = uri,
                    title = item.name,
                    durationMs = item.durationMs ?: it.durationMs,
                    status = MusicPlaybackStatus.Preparing,
                    statusLabel = if (seekMs != null) "Seeking…" else "Starting…",
                    isPlaying = true,
                    positionMs = startMs,
                    error = null,
                    active = true,
                )
            }
            val intent = Intent(context, MusicStreamService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_HOST, bot.host)
                putExtra(EXTRA_PORT, bot.port)
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_TITLE, item.name)
                putExtra(EXTRA_DURATION_MS, item.durationMs ?: 0)
                putExtra(EXTRA_RESET, reset && seekMs == null)
                if (seekMs != null) putExtra(EXTRA_SEEK_MS, seekMs)
            }
            context.startForegroundService(intent)
        }

        fun seek(context: Context, bot: BotService, item: MediaItem, positionMs: Int) {
            play(context = context, bot = bot, item = item, reset = false, seekMs = positionMs)
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, MusicStreamService::class.java).setAction(ACTION_PAUSE),
            )
        }

        fun resume(context: Context) {
            context.startService(
                Intent(context, MusicStreamService::class.java).setAction(ACTION_RESUME),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MusicStreamService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}

data class MusicStreamServiceState(
    val active: Boolean = false,
    val title: String = "No song",
    val status: MusicPlaybackStatus = MusicPlaybackStatus.Idle,
    val statusLabel: String = "Stopped",
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val error: String? = null,
    val uri: String? = null,
)

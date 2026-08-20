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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps `/play_wav` chunk POSTs alive while the UI is backgrounded
 * (wifi stream.md §5).
 */
class MusicStreamService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var feedJob: Job? = null

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
    ) {
        promoteForeground(title, "Playing on device", playing = true)

        // New song: interrupt any prior feed and reset the slice cursor.
        PlayWavFeed.cancel()
        feedJob?.cancel()
        if (reset) {
            PlayWavFeed.resetSliceStart()
            runCatching { PlayWavClient.stop(bot) }
        }

        activeBot = bot
        _state.update {
            it.copy(
                uri = uri.toString(),
                title = title,
                status = MusicPlaybackStatus.Preparing,
                statusLabel = "Starting…",
                isPlaying = true,
                positionMs = if (reset) 0 else it.positionMs,
                durationMs = durationMs,
                error = null,
                active = true,
            )
        }
        beginFeed(bot, uri, title, durationMs, reset)
    }

    private fun beginFeed(
        bot: BotService,
        uri: Uri,
        title: String,
        durationMs: Int,
        reset: Boolean,
    ) {
        feedJob?.cancel()
        feedJob = scope.launch {
            try {
                StreamingPcmDecoder(applicationContext, uri).use { decoder ->
                    val totalMs = decoder.durationMs.takeIf { it > 0 } ?: durationMs
                    _state.update {
                        it.copy(
                            status = MusicPlaybackStatus.Playing,
                            statusLabel = "Playing on device",
                            durationMs = totalMs,
                            isPlaying = true,
                            active = true,
                        )
                    }
                    updateNotification(title, "Playing on device", playing = true)

                    val startOffset = if (reset) 0 else PlayWavFeed.nextSliceStart
                    val completed = PlayWavFeed.streamLive(
                        bot = bot,
                        decoder = decoder,
                        startPcmOffset = startOffset,
                        onPlayhead = { offset ->
                            _state.update { state ->
                                state.copy(
                                    positionMs = WavPcm.durationMsForPcmBytes(offset)
                                        .coerceAtMost(totalMs.coerceAtLeast(1)),
                                )
                            }
                        },
                    )
                    if (!isActive) return@launch
                    if (completed) {
                        _state.update {
                            it.copy(
                                isPlaying = false,
                                status = MusicPlaybackStatus.Stopped,
                                statusLabel = "Finished",
                                positionMs = it.durationMs,
                                active = false,
                            )
                        }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            } catch (e: Exception) {
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
            beginFeed(
                bot = bot,
                uri = Uri.parse(uri),
                title = current.title,
                durationMs = current.durationMs,
                reset = false,
            )
        }
    }

    private fun stopPlayback(userStop: Boolean) {
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

        private const val CHANNEL_ID = "nino_music_stream"
        private const val NOTIFICATION_ID = 42

        private val _state = MutableStateFlow(MusicStreamServiceState())
        val state: StateFlow<MusicStreamServiceState> = _state.asStateFlow()

        @Volatile
        private var activeBot: BotService? = null

        fun play(context: Context, bot: BotService, item: MediaItem, reset: Boolean = true) {
            val uri = item.uri ?: return
            _state.update {
                it.copy(
                    uri = uri,
                    title = item.name,
                    durationMs = item.durationMs ?: it.durationMs,
                )
            }
            val intent = Intent(context, MusicStreamService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_HOST, bot.host)
                putExtra(EXTRA_PORT, bot.port)
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_TITLE, item.name)
                putExtra(EXTRA_DURATION_MS, item.durationMs ?: 0)
                putExtra(EXTRA_RESET, reset)
            }
            context.startForegroundService(intent)
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

package com.osuradio.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.osuradio.app.MainActivity
import com.osuradio.app.R
import com.osuradio.app.data.AudioTransition
import com.osuradio.app.data.ModSettings
import com.osuradio.app.data.SongMod
import com.osuradio.app.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MusicService : MediaSessionService() {
    private val TAG = "MusicService"
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    /** Wrapped player that redirects prev/next to ViewModel callbacks instead of seeking. */
    private lateinit var wrappedPlayer: ForwardingPlayer

    private var playerNotificationManager: PlayerNotificationManager? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var transitionJob: Job? = null
    private var modRampJob: Job? = null
    private var currentTransition: AudioTransition = AudioTransition.FADE_IN_OUT
    private val binder = LocalBinder()
    private lateinit var sessionActivityPendingIntent: PendingIntent

    /**
     * Called when the user taps "next" in the media notification.
     * Set by ViewModel to call skipToNext().
     */
    var onNextRequested: (() -> Unit)? = null

    /**
     * Called when the user taps "previous" in the media notification.
     * Set by ViewModel to call skipToPrev().
     */
    var onPrevRequested: (() -> Unit)? = null

    companion object {
        const val CHANNEL_ID = "osu_radio_playback"
        const val NOTIFICATION_ID = 1
    }

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val renderersFactory = DefaultRenderersFactory(this)
                .setEnableAudioFloatOutput(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

            player = ExoPlayer.Builder(this, renderersFactory)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()

            player.repeatMode = Player.REPEAT_MODE_OFF

            // Wrap the player so prev/next route to ViewModel callbacks
            // instead of trying to seek within a single-item queue.
            wrappedPlayer = object : ForwardingPlayer(player) {
                override fun seekToPreviousMediaItem() { onPrevRequested?.invoke() }
                override fun seekToNextMediaItem()     { onNextRequested?.invoke() }
                override fun seekToPrevious()          { onPrevRequested?.invoke() }
                override fun seekToNext()              { onNextRequested?.invoke() }

                // Keep the commands available so the notification buttons appear
                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .add(COMMAND_SEEK_TO_PREVIOUS)
                        .add(COMMAND_SEEK_TO_NEXT)
                        .build()
                }
            }

            val activityIntent = Intent(this, MainActivity::class.java)
            sessionActivityPendingIntent = PendingIntent.getActivity(
                this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            createNotificationChannel()

            mediaSession = MediaSession.Builder(this, wrappedPlayer)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()

            setupPlayerNotificationManager()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create MusicService", e)
        }
    }

    private fun setupPlayerNotificationManager() {
        try {
            playerNotificationManager = PlayerNotificationManager
                .Builder(this, NOTIFICATION_ID, CHANNEL_ID)
                .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(player: Player): CharSequence =
                        player.mediaMetadata.title ?: "Now Playing"

                    override fun getCurrentContentText(player: Player): CharSequence? =
                        player.mediaMetadata.artist ?: ""

                    // No cover art in the notification
                    override fun getCurrentLargeIcon(
                        player: Player,
                        callback: PlayerNotificationManager.BitmapCallback
                    ): android.graphics.Bitmap? = null

                    override fun createCurrentContentIntent(player: Player): PendingIntent? =
                        sessionActivityPendingIntent
                })
                .setSmallIconResourceId(R.drawable.ic_radio)
                .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                    override fun onNotificationPosted(
                        notificationId: Int,
                        notification: android.app.Notification,
                        ongoing: Boolean
                    ) {
                        if (ongoing) startForeground(notificationId, notification)
                    }
                    override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                })
                .build()

            // Use wrappedPlayer so the notification prev/next buttons fire our callbacks
            playerNotificationManager?.setPlayer(wrappedPlayer)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to setup PlayerNotificationManager", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Returns the raw ExoPlayer for attaching listeners in the ViewModel. */
    fun getPlayer(): ExoPlayer = player

    fun playAudio(
        path: String,
        title: String = "",
        artist: String = "",
        imagePath: String? = null,
        startMs: Long = 0L
    ) {
        try {
            // Store image path but do NOT embed artwork bytes — keep notification art-free
            val metadata = MediaMetadata.Builder()
                .setTitle(title.ifEmpty { File(path).nameWithoutExtension })
                .setArtist(artist)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(android.net.Uri.fromFile(File(path)))
                .setMediaMetadata(metadata)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            if (startMs > 0) player.seekTo(startMs)
            applyTransitionStart()
            player.play()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to play audio: $path", e)
        }
    }

    fun pauseResume() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun pause() {
        player.pause()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun applyMod(modSettings: ModSettings, currentPositionMs: Long) {
        try {
            modRampJob?.cancel()
            val wasPlaying = player.isPlaying
            when (modSettings.activeMod) {
                SongMod.WIND_UP   -> startModRamp(startSpeed = 1.0f, endSpeed = 1.8f, durationMs = 45_000L)
                SongMod.WIND_DOWN -> startModRamp(startSpeed = 1.0f, endSpeed = 0.6f, durationMs = 45_000L)
                else -> {
                    val (speed, pitch) = resolveModParams(modSettings)
                    player.setPlaybackParameters(PlaybackParameters(speed, pitch))
                }
            }
            player.seekTo(currentPositionMs)
            if (wasPlaying) player.play()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to apply mod", e)
        }
    }

    private fun startModRamp(startSpeed: Float, endSpeed: Float, durationMs: Long) {
        player.setPlaybackParameters(PlaybackParameters(startSpeed, startSpeed))
        val steps = 60
        val stepDelay = durationMs / steps
        modRampJob = scope.launch {
            for (i in 1..steps) {
                delay(stepDelay)
                val fraction = i / steps.toFloat()
                val speed = startSpeed + (endSpeed - startSpeed) * fraction
                player.setPlaybackParameters(PlaybackParameters(speed, speed))
            }
        }
    }

    private fun resolveModParams(modSettings: ModSettings): Pair<Float, Float> {
        return when (modSettings.activeMod) {
            SongMod.NONE         -> Pair(1.0f, 1.0f)
            SongMod.DAYCORE      -> Pair(0.75f, 0.75f)
            SongMod.NIGHTCORE    -> Pair(1.5f, 1.5f)
            SongMod.DOUBLE_TIME  -> Pair(1.5f, 1.0f)
            SongMod.HALF_TIME    -> Pair(0.75f, 1.0f)
            SongMod.WIND_UP      -> Pair(1.3f, 1.1f)
            SongMod.WIND_DOWN    -> Pair(0.8f, 0.9f)
            SongMod.BASS_BOOST   -> Pair(1.0f, 0.85f)
            SongMod.VAPORWAVE    -> Pair(0.7f, 0.7f)
            SongMod.CUSTOM_SPEED -> Pair(modSettings.customSpeed, modSettings.customSpeed)
        }
    }

    fun setTransition(transition: AudioTransition) {
        currentTransition = transition
    }

    fun stopWithTransition() {
        transitionJob?.cancel()
        when (currentTransition) {
            AudioTransition.FADE_IN_OUT, AudioTransition.CROSSFADE -> {
                transitionJob = scope.launch {
                    repeat(20) { i ->
                        player.volume = 1f - (i + 1) / 20f
                        delay(25)
                    }
                    player.stop()
                    player.volume = 1f
                }
            }
            else -> player.stop()
        }
    }

    private fun applyTransitionStart() {
        transitionJob?.cancel()
        when (currentTransition) {
            AudioTransition.FADE_IN_OUT, AudioTransition.CROSSFADE -> {
                player.volume = 0f
                transitionJob = scope.launch {
                    repeat(20) { i ->
                        player.volume = (i + 1) / 20f
                        delay(25)
                    }
                }
            }
            AudioTransition.SWOOSH -> {
                player.volume = 0f
                transitionJob = scope.launch {
                    delay(100)
                    repeat(10) { i ->
                        player.volume = (i + 1) / 10f
                        delay(30)
                    }
                }
            }
            AudioTransition.NONE -> player.volume = 1f
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "osu!radio playback controls"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        playerNotificationManager?.setPlayer(null)
        transitionJob?.cancel()
        modRampJob?.cancel()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

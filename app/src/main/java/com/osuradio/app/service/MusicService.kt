package com.osuradio.app.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.osuradio.app.MainActivity
import com.osuradio.app.data.AudioTransition
import com.osuradio.app.data.EqualizerSettings
import com.osuradio.app.data.ModSettings
import com.osuradio.app.data.RepeatMode
import com.osuradio.app.data.Song
import com.osuradio.app.data.SongMod
import com.osuradio.app.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MusicService : MediaLibraryService() {
    private val TAG = "MusicService"
    private lateinit var player: ExoPlayer
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var transitionJob: Job? = null
    private var modRampJob: Job? = null
    private var volumeFadeJob: Job? = null
    private var currentTransition: AudioTransition = AudioTransition.FADE_IN_OUT
    private val binder = LocalBinder()
    private lateinit var sessionActivityPendingIntent: PendingIntent

    // ── Audio effects ──────────────────────────────────────────────────────────
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var pendingEqSettings: EqualizerSettings = EqualizerSettings()
    private var pendingLoudnessEnabled: Boolean = false
    private var pendingLoudnessGainDb: Int = 3

    // ── Headphone reconnect tracking ───────────────────────────────────────────
    private var wasPlayingBeforeUnplug = false

    // ── Songs catalogue for Android Auto browsing ──────────────────────────────
    var allSongs: List<Song> = emptyList()

    // ── Android Auto library IDs ───────────────────────────────────────────────
    companion object {
        private const val ROOT_ID  = "root"
        private const val SONGS_ID = "songs"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    // ── Receivers ─────────────────────────────────────────────────────────────

    /** Handles wired headphone unplugging — pauses and remembers intent to resume. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            if (player.isPlaying) {
                wasPlayingBeforeUnplug = true
                player.pause()
            }
        }
    }

    /** Handles wired headphone reconnect — resumes if we paused due to unplug. */
    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_HEADSET_PLUG) return
            when (intent.getIntExtra("state", -1)) {
                1 -> { // Plugged in
                    if (wasPlayingBeforeUnplug && !player.isPlaying) {
                        scope.launch { delay(150); player.play() }
                        wasPlayingBeforeUnplug = false
                    }
                }
                0 -> { /* Unplugged — handled by noisyReceiver */ }
            }
        }
    }

    // ── Player listener ───────────────────────────────────────────────────────

    private val playerListener = object : Player.Listener {
        /**
         * Audio session ID changes after the audio renderer is initialized.
         * Create / recreate audio effects here to bind them to the correct session.
         */
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            createAudioEffects(audioSessionId)
        }
    }

    // ── Android Auto library callback ─────────────────────────────────────────

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        private val browserRoot = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("osu!radio")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            ).build()

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browserRoot, params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items: List<MediaItem> = when (parentId) {
                ROOT_ID  -> listOf(
                    MediaItem.Builder()
                        .setMediaId(SONGS_ID)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("Songs")
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build()
                        ).build()
                )
                SONGS_ID -> allSongs.map { it.toMediaItem() }
                else     -> emptyList()
            }
            val from = (page * pageSize).coerceAtMost(items.size)
            val to   = (from + pageSize).coerceAtMost(items.size)
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(items.subList(from, to)), params)
            )
        }

        /** Resolve mediaId → real URI so Android Auto can play from the browsable list. */
        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val resolved = mediaItems.map { item ->
                allSongs.find { it.id == item.mediaId }?.toMediaItem() ?: item
            }
            return Futures.immediateFuture(resolved)
        }

        override fun onSetMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val resolved = mediaItems.map { item ->
                allSongs.find { it.id == item.mediaId }?.toMediaItem() ?: item
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs)
            )
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
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
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
                // We manage BECOMING_NOISY ourselves via noisyReceiver for reconnect tracking
                .setHandleAudioBecomingNoisy(false)
                .build()

            player.repeatMode = Player.REPEAT_MODE_OFF
            // Hand audio decode off to DSP co-processor when idle → better battery life
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                        )
                        .build()
                )
                .build()
            player.addListener(playerListener)

            val activityIntent = Intent(this, MainActivity::class.java)
            sessionActivityPendingIntent = PendingIntent.getActivity(
                this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
                .setSessionActivity(sessionActivityPendingIntent)
                .build()

            // Register headphone receivers
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(headsetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create MusicService", e)
            // If the player was constructed but session setup failed partway through,
            // mediaLibrarySession stays null and onDestroy's cleanup path never runs —
            // release it here so we don't leak the native player/renderer resources.
            if (mediaLibrarySession == null && ::player.isInitialized) {
                player.release()
            }
        }
    }

    // ── onBind: return session binder for media controllers, local binder otherwise ──

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)?.let { return it }  // media session controller connection
        return binder                              // ViewModel connection
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
    }

    fun getPlayer(): ExoPlayer = player

    // ── Queue management (gapless) ────────────────────────────────────────────

    /**
     * Load [songs] into ExoPlayer as a full gapless playlist.
     * ExoPlayer pre-buffers ahead so transitions are seamless.
     */
    fun setQueue(songs: List<Song>) {
        allSongs = songs
        val mediaItems = songs.map { it.toMediaItem() }
        player.setMediaItems(mediaItems, /* resetPosition = */ false)
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
    }

    /**
     * Replace the queue but keep playback on the song at [startIndex] with its
     * elapsed position [positionMs]. Used when reshuffling so the current track
     * keeps playing uninterrupted.
     */
    fun setQueuePreservingPosition(songs: List<Song>, startIndex: Int, positionMs: Long) {
        allSongs = songs
        player.setMediaItems(songs.map { it.toMediaItem() }, startIndex, positionMs)
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
    }

    /** Seek to [index] in the current queue and begin playback. */
    fun playAtIndex(index: Int) {
        player.seekTo(index, 0L)
        applyTransitionStart()
        player.play()
    }

    fun pauseResume() { if (player.isPlaying) fadeOutPause() else fadeInPlay() }
    fun pause() { fadeOutPause() }
    fun pauseImmediately() { player.pause() }
    fun seekTo(ms: Long) { player.seekTo(ms) }

    fun setShuffleMode(enabled: Boolean) { player.shuffleModeEnabled = enabled }
    fun setRepeatMode(mode: RepeatMode) {
        player.repeatMode = when (mode) {
            RepeatMode.NONE -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL  -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE  -> Player.REPEAT_MODE_ONE
        }
    }

    // ── Audio effects ─────────────────────────────────────────────────────────

    fun applyEqualizerSettings(settings: EqualizerSettings) {
        pendingEqSettings = settings
        val sessionId = player.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
            applyEqToSession(sessionId, settings)
        }
    }

    fun applyLoudnessSettings(enabled: Boolean, gainDb: Int) {
        pendingLoudnessEnabled = enabled
        pendingLoudnessGainDb  = gainDb
        val sessionId = player.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
            applyLoudnessToSession(sessionId, enabled, gainDb)
        }
    }

    private fun createAudioEffects(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        applyEqToSession(sessionId, pendingEqSettings)
        applyLoudnessToSession(sessionId, pendingLoudnessEnabled, pendingLoudnessGainDb)
    }

    private fun applyEqToSession(sessionId: Int, settings: EqualizerSettings) {
        try {
            equalizer?.release()
            equalizer = Equalizer(0, sessionId).also { eq ->
                if (settings.enabled) {
                    val minLevel = eq.bandLevelRange[0].toInt()
                    val maxLevel = eq.bandLevelRange[1].toInt()
                    val count    = eq.numberOfBands.toInt()
                    for (i in 0 until minOf(count, settings.bandLevels.size)) {
                        val clamped = settings.bandLevels[i].coerceIn(minLevel, maxLevel)
                        eq.setBandLevel(i.toShort(), clamped.toShort())
                    }
                }
                eq.enabled = settings.enabled
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create Equalizer for session $sessionId", e)
        }
    }

    private fun applyLoudnessToSession(sessionId: Int, enabled: Boolean, gainDb: Int) {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(sessionId).also { le ->
                if (enabled) le.setTargetGain(gainDb * 100)  // dB → mB
                le.enabled = enabled
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create LoudnessEnhancer for session $sessionId", e)
        }
    }

    // ── Mods ──────────────────────────────────────────────────────────────────

    fun applyMod(modSettings: ModSettings, currentPositionMs: Long) {
        try {
            modRampJob?.cancel()
            val wasPlaying = player.isPlaying
            when (modSettings.activeMod) {
                SongMod.WIND_UP   -> startModRamp(1.0f, 1.8f, 45_000L)
                SongMod.WIND_DOWN -> startModRamp(1.0f, 0.6f, 45_000L)
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
        modRampJob = scope.launch {
            repeat(60) { i ->
                delay(durationMs / 60)
                val fraction = (i + 1) / 60f
                val speed = startSpeed + (endSpeed - startSpeed) * fraction
                player.setPlaybackParameters(PlaybackParameters(speed, speed))
            }
        }
    }

    private fun resolveModParams(modSettings: ModSettings): Pair<Float, Float> = when (modSettings.activeMod) {
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

    // ── Transitions ───────────────────────────────────────────────────────────

    fun setTransition(transition: AudioTransition) { currentTransition = transition }

    private fun fadeOutPause() {
        transitionJob?.cancel()
        volumeFadeJob?.cancel()
        if (currentTransition == AudioTransition.NONE) { player.pause(); return }
        volumeFadeJob = scope.launch {
            val start = player.volume.coerceIn(0f, 1f)
            repeat(12) { i ->
                player.volume = start * (1f - (i + 1) / 12f)
                delay(20)
            }
            player.pause()
        }
    }

    private fun fadeInPlay() {
        transitionJob?.cancel()
        volumeFadeJob?.cancel()
        player.play()
        if (currentTransition == AudioTransition.NONE) { player.volume = 1f; return }
        volumeFadeJob = scope.launch {
            val start = player.volume.coerceIn(0f, 1f)
            repeat(12) { i ->
                player.volume = start + (1f - start) * ((i + 1) / 12f)
                delay(20)
            }
        }
    }

    private fun applyTransitionStart() {
        transitionJob?.cancel()
        volumeFadeJob?.cancel()
        when (currentTransition) {
            AudioTransition.FADE_IN_OUT, AudioTransition.CROSSFADE -> {
                player.volume = 0f
                transitionJob = scope.launch {
                    repeat(20) { i -> player.volume = (i + 1) / 20f; delay(25) }
                }
            }
            AudioTransition.SWOOSH -> {
                player.volume = 0f
                transitionJob = scope.launch {
                    delay(100)
                    repeat(10) { i -> player.volume = (i + 1) / 10f; delay(30) }
                }
            }
            AudioTransition.NONE -> player.volume = 1f
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaLibrarySession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(headsetReceiver) } catch (_: Exception) {}
        equalizer?.release()
        loudnessEnhancer?.release()
        transitionJob?.cancel()
        modRampJob?.cancel()
        volumeFadeJob?.cancel()
        scope.cancel()
        mediaLibrarySession?.run { player.release(); release(); mediaLibrarySession = null }
        super.onDestroy()
    }
}

// ── Extension ─────────────────────────────────────────────────────────────────

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(Uri.fromFile(File(audioPath)))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(imagePath?.let { Uri.fromFile(File(it)) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()

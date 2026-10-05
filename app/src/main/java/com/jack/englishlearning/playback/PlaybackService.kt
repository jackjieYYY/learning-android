package com.jack.englishlearning.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jack.englishlearning.MainActivity
import com.jack.englishlearning.data.LibraryRepository

/** Position to persist: a finished video resumes from the start next time. */
internal fun resumePosition(player: Player): Long =
    if (player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition

/** The URI travels as the media ID because a controller does not pass local configuration to the session. */
internal fun resolveMediaItem(item: MediaItem): MediaItem =
    if (item.localConfiguration != null || item.mediaId.isEmpty()) item else item.buildUpon().setUri(item.mediaId).build()

/**
 * Owns the ExoPlayer so lessons keep playing with the screen locked or the app in the background.
 * Media3 turns it into a foreground service with a media notification and lock-screen controls
 * while playing; the study screen connects through a MediaController.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var repository: LibraryRepository
    private val handler = Handler(Looper.getMainLooper())
    private val periodicSave = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = LibraryRepository(applicationContext)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                savePosition()
                handler.removeCallbacks(periodicSave)
                if (isPlaying) handler.postDelayed(periodicSave, SAVE_INTERVAL_MS)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                // The study screen clears the item when the user leaves the lesson.
                if (player.mediaItemCount == 0) stopSelf()
            }
        })
        val openApp = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                                             mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
                    Futures.immediateFuture(mediaItems.map(::resolveMediaItem).toMutableList())
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED) stopSelf()
    }

    override fun onDestroy() {
        savePosition()
        handler.removeCallbacks(periodicSave)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun savePosition() {
        val player = session?.player ?: return
        val uri = player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        repository.savePosition(uri, resumePosition(player))
    }

    private companion object { const val SAVE_INTERVAL_MS = 5_000L }
}

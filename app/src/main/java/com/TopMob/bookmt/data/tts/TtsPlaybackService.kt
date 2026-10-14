package com.TopMob.bookmt.data.tts

import android.app.*
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.TopMob.bookmt.MainActivity
import com.TopMob.bookmt.R
import com.TopMob.bookmt.domain.tts.TtsEngine
import com.TopMob.bookmt.domain.tts.TtsStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/** Keeps local speech alive while the reader is backgrounded or the screen is off. */
@AndroidEntryPoint
class TtsPlaybackService : Service() {
    @Inject lateinit var engine: TtsEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(
            CHANNEL, getString(R.string.tts_title), NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "BookMT speech").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = engine.resume()
                override fun onPause() = engine.pause()
                override fun onStop() = engine.stop()
            })
            isActive = true
        }
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BookMT:ReadAloud")
        startForeground(NOTIFICATION, notification(engine.state.value.status))
        ready.value = true
        scope.launch {
            engine.state.collect { state ->
                val active = state.status == TtsStatus.PLAYING || state.status == TtsStatus.PREPARING
                // Continuous user-started playback; always released on pause/stop/destroy.
                if (active && !wakeLock.isHeld) wakeLock.acquire()
                if (!active && wakeLock.isHeld) wakeLock.release()
                if (state.status in setOf(TtsStatus.IDLE, TtsStatus.STOPPED, TtsStatus.ERROR)) {
                    ready.value = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    session.setPlaybackState(PlaybackState.Builder()
                        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                        .setState(if (state.status == TtsStatus.PAUSED) PlaybackState.STATE_PAUSED
                            else if (state.status == TtsStatus.PREPARING) PlaybackState.STATE_BUFFERING else PlaybackState.STATE_PLAYING,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
                    manager.notify(NOTIFICATION, notification(state.status))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PAUSE -> engine.pause()
            RESUME -> engine.resume()
            STOP -> engine.stop()
        }
        return START_NOT_STICKY
    }

    private fun notification(status: TtsStatus): Notification {
        val paused = status == TtsStatus.PAUSED
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String) = PendingIntent.getService(this, name.hashCode(),
            Intent(this, TtsPlaybackService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
            else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setSmallIcon(R.drawable.ic_stat_speech)
            .setContentTitle(getString(R.string.tts_title))
            .setContentText(getString(if (paused) R.string.tts_paused else if (status == TtsStatus.PREPARING)
                R.string.tts_preparing else R.string.tts_playing))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1))
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this,
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause),
                getString(if (paused) R.string.tts_resume else R.string.tts_pause), action(if (paused) RESUME else PAUSE)).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this,
                android.R.drawable.ic_menu_close_clear_cancel), getString(R.string.reader_stop_tts), action(STOP)).build())
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        engine.stop()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ready.value = false
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        session.release()
        if (engine.state.value.status in setOf(TtsStatus.PLAYING, TtsStatus.PREPARING, TtsStatus.PAUSED)) engine.stop()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        val ready = MutableStateFlow(false)
        private const val CHANNEL = "offline_speech"
        private const val NOTIFICATION = 701
        private const val PAUSE = "com.TopMob.bookmt.tts.PAUSE"
        private const val RESUME = "com.TopMob.bookmt.tts.RESUME"
        private const val STOP = "com.TopMob.bookmt.tts.STOP"
    }
}

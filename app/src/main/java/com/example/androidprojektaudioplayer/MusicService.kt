package com.example.androidprojektaudioplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Hintergrund-Service, der die Musikwiedergabe verwaltet.
 * Läuft als Foreground-Service mit Benachrichtigung und Steuerungselementen.
 * Speichert regelmäßig den Wiedergabestatus in SharedPreferences.
 */
class MusicService : Service() {

    private val binder = MusicBinder()
    val mediaPlayer = MediaPlayer()
    var currentTrack: myAudio? = null
    var currentIndex: Int = -1
    var trackList: MutableList<myAudio> = mutableListOf()

    private val CHANNEL_ID = "MusicPlayerChannel"
    private val NOTIFICATION_ID = 1

    private val saveStateHandler = Handler(Looper.getMainLooper())

    /** Speichert den Wiedergabestatus alle 5 Sekunden (statt 100ms – schont IO und Main-Thread). */
    private val saveStateRunnable = object : Runnable {
        override fun run() {
            savePlaybackState()
            saveStateHandler.postDelayed(this, 5000)
        }
    }

    /** Callback für Track-Wechsel – wird von der Activity gesetzt. */
    var onTrackChanged: ((myAudio) -> Unit)? = null

    /** Callback für Play/Pause-Wechsel – wird von der Activity gesetzt. */
    var onPlayStateChanged: ((Boolean) -> Unit)? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        saveStateHandler.post(saveStateRunnable)
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY_PAUSE" -> togglePlayPause()
            "NEXT" -> next()
            "PREVIOUS" -> previous()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaPlayer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Lädt einen Track in den MediaPlayer, ohne ihn abzuspielen.
     * Wird zum Wiederherstellen des letzten Zustands nach App-Neustart verwendet.
     */
    fun loadTrack(track: myAudio, index: Int) {
        currentTrack = track
        currentIndex = index

        mediaPlayer.reset()
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        mediaPlayer.setDataSource(applicationContext, Uri.parse(track.audioPath))
        mediaPlayer.prepare()

        onTrackChanged?.invoke(track)
        onPlayStateChanged?.invoke(false)
    }

    /** Lädt einen Track und startet die Wiedergabe sofort. */
    fun playTrack(track: myAudio, index: Int) {
        loadTrack(track, index)
        mediaPlayer.start()
        startForeground(NOTIFICATION_ID, createNotification())
        savePlaybackState()
        onTrackChanged?.invoke(track)
        onPlayStateChanged?.invoke(true)
    }

    fun pause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            updateNotification()
            savePlaybackState()
            onPlayStateChanged?.invoke(false)
        }
    }

    fun resume() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            updateNotification()
            savePlaybackState()
            onPlayStateChanged?.invoke(true)
        }
    }

    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) pause() else resume()
    }

    fun next() {
        if (trackList.isNotEmpty()) {
            val newIndex = (currentIndex + 1) % trackList.size
            playTrack(trackList[newIndex], newIndex)
        }
    }

    fun previous() {
        if (trackList.isNotEmpty()) {
            val newIndex = if (currentIndex > 0) currentIndex - 1 else trackList.size - 1
            playTrack(trackList[newIndex], newIndex)
        }
    }

    /** Erstellt den Notification-Channel. Kein Build.VERSION-Check nötig (minSdk >= 26). */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music Player",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Music playback controls"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Erstellt die Foreground-Benachrichtigung mit Play/Pause, Vor und Zurück. */
    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousPendingIntent = createServicePendingIntent("PREVIOUS", 1)
        val playPausePendingIntent = createServicePendingIntent("PLAY_PAUSE", 2)
        val nextPendingIntent = createServicePendingIntent("NEXT", 3)

        val playPauseIcon = if (mediaPlayer.isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTrack?.audioTitle ?: getString(R.string.app_name))
            .setContentText(currentTrack?.audioArtist ?: "")
            .setSmallIcon(R.drawable.library_music_40px)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.skip_previous_24px, getString(R.string.previous), previousPendingIntent)
            .addAction(playPauseIcon, getString(R.string.playPause), playPausePendingIntent)
            .addAction(R.drawable.skip_next_24px, getString(R.string.next), nextPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(true)
            .build()
    }

    /** Hilfsmethode – erstellt einen PendingIntent für eine Service-Aktion. */
    private fun createServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification())
    }

    /** Speichert den Wiedergabestatus asynchron (apply() statt commit()). */
    private fun savePlaybackState() {
        if (currentTrack == null) return
        applicationContext.getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
            .edit().apply {
                putInt("currentTrackID", currentTrack?.audioID ?: -1)
                putInt("currentPosition", mediaPlayer.currentPosition)
                putInt("currentIndex", currentIndex)
                putBoolean("wasPlaying", mediaPlayer.isPlaying)
                apply()
            }
    }

    override fun onDestroy() {
        saveStateHandler.removeCallbacks(saveStateRunnable)
        savePlaybackState()
        mediaPlayer.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

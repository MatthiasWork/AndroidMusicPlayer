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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class MusicService : Service() {
    private val binder = MusicBinder()
    val mediaPlayer = MediaPlayer()
    var currentTrack: myAudio? = null
    var currentIndex: Int = -1
    var trackList: MutableList<myAudio> = mutableListOf()

    private val CHANNEL_ID = "MusicPlayerChannel"
    private val NOTIFICATION_ID = 1

    // Handler für regelmäßiges Speichern
    private val saveStateHandler = Handler(Looper.getMainLooper())
    private val saveStateRunnable = object : Runnable {
        override fun run() {
            savePlaybackState()
            saveStateHandler.postDelayed(this, 1000)  // Jede Sekunde
        }
    }

    // Callbacks für UI-Updates
    var onTrackChanged: ((myAudio) -> Unit)? = null
    var onPlayStateChanged: ((Boolean) -> Unit)? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        saveStateHandler.post(saveStateRunnable)  // Starten
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY_PAUSE" -> togglePlayPause()
            "NEXT" -> next()
            "PREVIOUS" -> previous()
        }
        return START_STICKY
    }

    fun playTrack(track: myAudio, index: Int) {
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
        mediaPlayer.start()

        // Vordergrund-Service starten mit Notification
        startForeground(NOTIFICATION_ID, createNotification())

        savePlaybackState()  // State speichern
        onTrackChanged?.invoke(track)
        onPlayStateChanged?.invoke(true)
    }

    fun pause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            updateNotification()
            savePlaybackState()  // State speichern
            onPlayStateChanged?.invoke(false)
        }
    }

    fun resume() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            updateNotification()
            savePlaybackState()  // State speichern
            onPlayStateChanged?.invoke(true)
        }
    }

    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun next() {
        if (trackList.isNotEmpty()) {
            val newIndex = if (currentIndex < trackList.size - 1) {
                currentIndex + 1
            } else {
                0
            }
            playTrack(trackList[newIndex], newIndex)
        }
    }

    fun previous() {
        if (trackList.isNotEmpty()) {
            val newIndex = if (currentIndex > 0) {
                currentIndex - 1
            } else {
                trackList.size - 1
            }
            playTrack(trackList[newIndex], newIndex)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = Intent(this, MusicService::class.java).apply {
            action = "PREVIOUS"
        }
        val previousPendingIntent = PendingIntent.getService(
            this, 1, previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, MusicService::class.java).apply {
            action = "PLAY_PAUSE"
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicService::class.java).apply {
            action = "NEXT"
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (mediaPlayer.isPlaying) {
            R.drawable.pause_24px
        } else {
            R.drawable.play_arrow_24px
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTrack?.audioTitle ?: getString(R.string.app_name))
            .setContentText(currentTrack?.audioArtist ?: "")
            .setSmallIcon(R.drawable.library_music_40px)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.skip_previous_24px, getString(R.string.previous), previousPendingIntent)
            .addAction(playPauseIcon, getString(R.string.playPause), playPausePendingIntent)
            .addAction(R.drawable.skip_next_24px, getString(R.string.next), nextPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun savePlaybackState() {
        val prefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("currentTrackID", currentTrack?.audioID ?: -1)
            putInt("currentPosition", mediaPlayer.currentPosition)
            putInt("currentIndex", currentIndex)
            putBoolean("wasPlaying", mediaPlayer.isPlaying)
            apply()
        }
    }

    override fun onDestroy() {
        saveStateHandler.removeCallbacks(saveStateRunnable)  // Stoppen
        savePlaybackState()
        mediaPlayer.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
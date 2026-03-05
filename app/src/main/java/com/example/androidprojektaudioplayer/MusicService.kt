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
 * Läuft als Foreground-Service mit einer persistenten Benachrichtigung,
 * die Play/Pause-, Vor- und Zurück-Steuerungselemente enthält.
 *
 * Der Service speichert regelmäßig den Wiedergabestatus in SharedPreferences,
 * damit nach einem App-Neustart der letzte Zustand wiederhergestellt werden kann.
 *
 * Kommuniziert über Callbacks (onTrackChanged, onPlayStateChanged) mit den Activities.
 */
class MusicService : Service() {

    /** Binder-Instanz für die Service-Anbindung über ServiceConnection. */
    private val binder = MusicBinder()

    /** Der MediaPlayer für die Audio-Wiedergabe. */
    val mediaPlayer = MediaPlayer()

    /** Der aktuell geladene/spielende Track (null, wenn keiner geladen ist). */
    var currentTrack: myAudio? = null

    /** Index des aktuellen Tracks in der trackList (-1 = kein Track geladen). */
    var currentIndex: Int = -1

    /** Die aktuelle Wiedergabeliste – wird von der Activity gesetzt. */
    var trackList: MutableList<myAudio> = mutableListOf()

    /** ID des Notification-Channels für die Musikwiedergabe. */
    private val CHANNEL_ID = "MusicPlayerChannel"

    /** Feste Notification-ID für die Foreground-Benachrichtigung. */
    private val NOTIFICATION_ID = 1

    /** Handler für den periodischen Speicher-Runnable (läuft auf dem Main-Thread). */
    private val saveStateHandler = Handler(Looper.getMainLooper())

    /**
     * Runnable, das den Wiedergabestatus alle 5 Sekunden in SharedPreferences speichert.
     * Das Intervall von 5 Sekunden ist ein Kompromiss zwischen Genauigkeit und IO-Last.
     */
    private val saveStateRunnable = object : Runnable {
        override fun run() {
            savePlaybackState()
            saveStateHandler.postDelayed(this, 5000)
        }
    }

    /** Callback für Track-Wechsel – wird von der gebundenen Activity gesetzt. */
    var onTrackChanged: ((myAudio) -> Unit)? = null

    /** Callback für Play/Pause-Statusänderung – wird von der gebundenen Activity gesetzt. */
    var onPlayStateChanged: ((Boolean) -> Unit)? = null

    /**
     * Innere Binder-Klasse, die den Zugriff auf die MusicService-Instanz ermöglicht.
     * Die Activity ruft getService() auf, um Methoden des Services direkt aufzurufen.
     */
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    /**
     * Wird beim Erstellen des Services aufgerufen.
     * Erstellt den Notification-Channel und startet den periodischen Speicher-Runnable.
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        saveStateHandler.post(saveStateRunnable)
    }

    /**
     * Gibt den Binder zurück, wenn eine Activity sich an den Service bindet.
     */
    override fun onBind(intent: Intent): IBinder = binder

    /**
     * Verarbeitet Steuerungsbefehle aus der Notification (Play/Pause, Vor, Zurück).
     * START_NOT_STICKY: Service wird nicht automatisch neu gestartet, wenn er beendet wird.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY_PAUSE" -> togglePlayPause()
            "NEXT" -> next()
            "PREVIOUS" -> previous()
        }
        return START_NOT_STICKY
    }

    /**
     * Wird aufgerufen, wenn die App aus den "Recents" entfernt wird.
     * Stoppt die Wiedergabe, entfernt die Benachrichtigung und beendet den Service.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaPlayer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Lädt einen Track in den MediaPlayer, ohne ihn abzuspielen.
     * Wird zum Wiederherstellen des letzten Zustands nach einem App-Neustart verwendet.
     *
     * Setzt den MediaPlayer zurück, konfiguriert die AudioAttributes für Musikwiedergabe
     * und bereitet den Track vor (prepare).
     *
     * @param track Der zu ladende Track
     * @param index Der Index des Tracks in der Wiedergabeliste
     */
    fun loadTrack(track: myAudio, index: Int) {
        currentTrack = track
        currentIndex = index

        // MediaPlayer zurücksetzen und neu konfigurieren
        mediaPlayer.reset()
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        mediaPlayer.setDataSource(applicationContext, Uri.parse(track.audioPath))
        mediaPlayer.prepare()  // Synchrones Prepare – blockiert kurz, ist aber für lokale Dateien OK
        mediaPlayer.setOnCompletionListener { next() }
        // Activities über den neuen Track informieren
        onTrackChanged?.invoke(track)
        onPlayStateChanged?.invoke(false)
    }

    /**
     * Lädt einen Track und startet die Wiedergabe sofort.
     * Startet den Foreground-Service mit der Benachrichtigung.
     *
     * @param track Der abzuspielende Track
     * @param index Der Index des Tracks in der Wiedergabeliste
     */
    fun playTrack(track: myAudio, index: Int) {
        loadTrack(track, index)
        mediaPlayer.start()
        startForeground(NOTIFICATION_ID, createNotification())
        savePlaybackState()
        onTrackChanged?.invoke(track)
        onPlayStateChanged?.invoke(true)
    }

    /** Pausiert die aktuelle Wiedergabe und aktualisiert Notification und Status. */
    fun pause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            updateNotification()
            savePlaybackState()
            onPlayStateChanged?.invoke(false)
        }
    }

    /** Setzt die pausierte Wiedergabe fort und aktualisiert Notification und Status. */
    fun resume() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            updateNotification()
            savePlaybackState()
            onPlayStateChanged?.invoke(true)
        }
    }

    /** Wechselt zwischen Play und Pause (Toggle-Funktion). */
    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) pause() else resume()
    }

    /**
     * Springt zum nächsten Track in der Wiedergabeliste.
     * Nutzt Modulo-Operation für Endlos-Wiedergabe (letzter -> erster Track).
     */
    fun next() {
        if (trackList.isNotEmpty()) {
            val newIndex = (currentIndex + 1) % trackList.size
            playTrack(trackList[newIndex], newIndex)
        }
    }

    /**
     * Springt zum vorherigen Track in der Wiedergabeliste.
     * Beim ersten Track wird zum letzten gesprungen (Endlos-Wiedergabe).
     */
    fun previous() {
        if (trackList.isNotEmpty()) {
            val newIndex = if (currentIndex > 0) currentIndex - 1 else trackList.size - 1
            playTrack(trackList[newIndex], newIndex)
        }
    }

    /**
     * Erstellt den Notification-Channel für die Musikwiedergabe.
     * IMPORTANCE_LOW: Kein Ton bei Benachrichtigung (wichtig für einen Musikplayer).
     * Kein Build.VERSION-Check nötig, da minSdk >= 26.
     */
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

    /**
     * Erstellt die Foreground-Benachrichtigung mit Steuerungselementen.
     * Enthält drei Aktionen: Zurück, Play/Pause, Vor.
     * Ein Klick auf die Benachrichtigung selbst öffnet die MainActivity.
     *
     * @return Die fertig konfigurierte Notification
     */
    private fun createNotification(): Notification {
        // PendingIntent zum Öffnen der MainActivity bei Klick auf die Notification
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP  // Bestehende Activity wiederverwenden
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntents für die drei Steuerungsbuttons
        val previousPendingIntent = createServicePendingIntent("PREVIOUS", 1)
        val playPausePendingIntent = createServicePendingIntent("PLAY_PAUSE", 2)
        val nextPendingIntent = createServicePendingIntent("NEXT", 3)

        // Icon je nach aktuellem Wiedergabestatus
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
                // MediaStyle zeigt die Steuerungselemente in der kompakten Ansicht
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)  // Alle drei Buttons anzeigen
            )
            .setOngoing(true)  // Nicht vom Benutzer wegwischbar
            .build()
    }

    /**
     * Hilfsmethode – erstellt einen PendingIntent, der eine Aktion an diesen Service sendet.
     * Wird für die Notification-Buttons (Play/Pause, Vor, Zurück) verwendet.
     *
     * @param action      Die Aktion als String (z.B. "PLAY_PAUSE", "NEXT", "PREVIOUS")
     * @param requestCode Eindeutiger Request-Code pro Aktion
     * @return Der konfigurierte PendingIntent
     */
    private fun createServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Aktualisiert die bestehende Benachrichtigung (z.B. nach Play/Pause-Wechsel). */
    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification())
    }

    /**
     * Speichert den aktuellen Wiedergabestatus asynchron in SharedPreferences.
     * Verwendet apply() statt commit() für nicht-blockierendes Schreiben.
     *
     * Gespeicherte Werte:
     * - currentTrackID: ID des aktuellen Tracks
     * - currentPosition: Aktuelle Wiedergabeposition in Millisekunden
     * - currentIndex: Index in der Wiedergabeliste
     * - wasPlaying: Ob die Wiedergabe lief (für Resume nach App-Neustart)
     */
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

    /**
     * Wird beim Zerstören des Services aufgerufen.
     * Stoppt den periodischen Speicher-Runnable, speichert den letzten Status,
     * gibt den MediaPlayer frei und entfernt die Foreground-Benachrichtigung.
     */
    override fun onDestroy() {
        saveStateHandler.removeCallbacks(saveStateRunnable)
        savePlaybackState()
        mediaPlayer.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

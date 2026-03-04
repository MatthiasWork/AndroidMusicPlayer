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

/**
 * Hintergrund-Service, der die Musikwiedergabe verwaltet.
 * Läuft als Foreground-Service mit einer Benachrichtigung, die Steuerungselemente
 * (Play/Pause, Vor, Zurück) anzeigt. Speichert regelmäßig den Wiedergabestatus
 * in SharedPreferences, damit die Wiedergabe nach einem Neustart fortgesetzt werden kann.
 */
class MusicService : Service() {

    /** Binder-Instanz für die Kommunikation zwischen Activity und Service. */
    private val binder = MusicBinder()

    /** Der MediaPlayer, der die eigentliche Audiowiedergabe übernimmt. */
    val mediaPlayer = MediaPlayer()

    /** Der aktuell geladene/wiedergegebene Track (null, wenn kein Track geladen ist). */
    var currentTrack: myAudio? = null

    /** Index des aktuellen Tracks in der trackList (-1, wenn kein Track ausgewählt ist). */
    var currentIndex: Int = -1

    /** Die aktuelle Wiedergabeliste, die alle abspielbaren Songs enthält. */
    var trackList: MutableList<myAudio> = mutableListOf()

    /** ID des Notification-Channels für die Musikwiedergabe-Benachrichtigung. */
    private val CHANNEL_ID = "MusicPlayerChannel"

    /** Feste ID der Benachrichtigung, um sie aktualisieren zu können. */
    private val NOTIFICATION_ID = 1

    /** Handler für das regelmäßige Speichern des Wiedergabestatus. */
    private val saveStateHandler = Handler(Looper.getMainLooper())

    /**
     * Runnable, das alle 100ms den aktuellen Wiedergabestatus
     * (Track-ID, Position, Abspielmodus) in SharedPreferences sichert.
     */
    private val saveStateRunnable = object : Runnable {
        override fun run() {
            savePlaybackState()
            saveStateHandler.postDelayed(this, 100)
        }
    }

    /**
     * Callback-Funktion, die aufgerufen wird, wenn ein neuer Track geladen wird.
     * Wird von der Activity gesetzt, um die UI zu aktualisieren (Titel, Künstler etc.).
     */
    var onTrackChanged: ((myAudio) -> Unit)? = null

    /**
     * Callback-Funktion, die aufgerufen wird, wenn sich der Play/Pause-Status ändert.
     * Wird von der Activity gesetzt, um das Play/Pause-Icon zu aktualisieren.
     */
    var onPlayStateChanged: ((Boolean) -> Unit)? = null

    /**
     * Innere Binder-Klasse, die es Activities ermöglicht, eine Referenz auf den
     * MusicService zu erhalten und dessen öffentliche Methoden aufzurufen.
     */
    inner class MusicBinder : Binder() {
        /**
         * Gibt die Instanz des MusicService zurück.
         *
         * @return Die aktuelle MusicService-Instanz
         */
        fun getService(): MusicService = this@MusicService
    }

    /**
     * Wird beim Erstellen des Service aufgerufen.
     * Erstellt den Notification-Channel und startet das regelmäßige Speichern
     * des Wiedergabestatus.
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        saveStateHandler.post(saveStateRunnable)
    }

    /**
     * Wird aufgerufen, wenn eine Activity sich an den Service bindet.
     * Gibt den MusicBinder zurück, über den die Activity Zugriff auf den Service erhält.
     *
     * @param intent Der Intent, mit dem der Service gebunden wurde
     * @return Der MusicBinder für die Kommunikation
     */
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /**
     * Wird aufgerufen, wenn der Service über einen Intent gestartet oder eine Aktion
     * über die Notification ausgelöst wird. Verarbeitet die Aktionen PLAY_PAUSE, NEXT und PREVIOUS.
     *
     * @param intent Der Intent mit der Aktion (z. B. "PLAY_PAUSE", "NEXT", "PREVIOUS")
     * @param flags  Zusätzliche Flags
     * @param startId Eindeutige ID für diesen Start-Aufruf
     * @return START_STICKY, damit der Service nach einem Kill automatisch neu gestartet wird
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY_PAUSE" -> togglePlayPause()
            "NEXT" -> next()
            "PREVIOUS" -> previous()
        };
        return START_NOT_STICKY;
    }

    /**
     * Wird aufgerufen, wenn die App aus der Task-Übersicht (Recents) weggewischt wird.
     * Stoppt die Wiedergabe, entfernt die Notification und beendet den Service.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaPlayer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Lädt einen Track in den MediaPlayer, ohne ihn automatisch abzuspielen.
     * Wird verwendet, um den zuletzt gehörten Track nach einem App-Neustart
     * wiederherzustellen, ohne dass die Wiedergabe sofort startet.
     *
     * @param track Der zu ladende Audiotitel
     * @param index Der Index des Tracks in der trackList
     */
    fun loadTrack(track: myAudio, index: Int) {
        currentTrack = track
        currentIndex = index

        // MediaPlayer zurücksetzen und mit den richtigen Audio-Attributen konfigurieren
        mediaPlayer.reset()
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        mediaPlayer.setDataSource(applicationContext, Uri.parse(track.audioPath))
        mediaPlayer.prepare()   // Synchron vorbereiten, NICHT starten

        // UI-Callbacks auslösen, damit die Anzeige aktualisiert wird
        onTrackChanged?.invoke(track)
        onPlayStateChanged?.invoke(false)
    }

    /**
     * Lädt einen Track in den MediaPlayer und startet die Wiedergabe sofort.
     * Startet den Foreground-Service mit einer Benachrichtigung und speichert
     * den aktuellen Wiedergabestatus.
     *
     * @param track Der abzuspielende Audiotitel
     * @param index Der Index des Tracks in der trackList
     */
    fun playTrack(track: myAudio, index: Int) {
        android.util.Log.d("MusicService", "playTrack START: ${track.audioTitle}, isPlaying=${mediaPlayer.isPlaying}")
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
        mediaPlayer.prepare()

        // Wiedergabe starten
        mediaPlayer.start()

        // Foreground-Service mit Notification starten, damit der Service nicht vom System beendet wird
        startForeground(NOTIFICATION_ID, createNotification())

        savePlaybackState()
        onTrackChanged?.invoke(track)
        onPlayStateChanged?.invoke(true)
    }

    /**
     * Pausiert die aktuelle Wiedergabe, aktualisiert die Benachrichtigung
     * und speichert den Wiedergabestatus. Macht nichts, wenn gerade kein Song spielt.
     */
    fun pause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            updateNotification()
            savePlaybackState()
            onPlayStateChanged?.invoke(false)
        }
    }

    /**
     * Setzt die pausierte Wiedergabe fort, aktualisiert die Benachrichtigung
     * und speichert den Wiedergabestatus. Macht nichts, wenn der Song bereits läuft.
     */
    fun resume() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            updateNotification()
            savePlaybackState()
            onPlayStateChanged?.invoke(true)
        }
    }

    /**
     * Wechselt zwischen Play und Pause:
     * Wenn der Song läuft, wird er pausiert; wenn er pausiert ist, wird er fortgesetzt.
     */
    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    /**
     * Springt zum nächsten Song in der Wiedergabeliste.
     * Am Ende der Liste wird wieder beim ersten Song begonnen (Loop).
     */
    fun next() {
        if (trackList.isNotEmpty()) {
            val newIndex = if (currentIndex < trackList.size - 1) {
                currentIndex + 1
            } else {
                0  // Am Ende der Liste: Zurück zum Anfang
            }
            playTrack(trackList[newIndex], newIndex)
        }
    }

    /**
     * Springt zum vorherigen Song in der Wiedergabeliste.
     * Am Anfang der Liste wird zum letzten Song gesprungen (Loop).
     */
    fun previous() {
        if (trackList.isNotEmpty()) {
            val newIndex = if (currentIndex > 0) {
                currentIndex - 1
            } else {
                trackList.size - 1  // Am Anfang der Liste: Zum letzten Song
            }
            playTrack(trackList[newIndex], newIndex)
        }
    }

    /**
     * Erstellt den Notification-Channel für die Musikwiedergabe-Benachrichtigung.
     * Wird nur ab Android O (API 26) benötigt, da ältere Versionen keine Channels verwenden.
     * Die Priorität ist LOW, damit kein störender Ton bei der Benachrichtigung abgespielt wird.
     */
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

    /**
     * Erstellt die Foreground-Benachrichtigung mit Steuerungselementen.
     * Die Benachrichtigung zeigt den aktuellen Titel und Künstler an und bietet
     * drei Aktionen: Vorheriger Song, Play/Pause und Nächster Song.
     * Ein Klick auf die Benachrichtigung selbst öffnet die MainActivity.
     *
     * @return Das fertig konfigurierte Notification-Objekt
     */
    private fun createNotification(): Notification {
        // PendingIntent zum Öffnen der App beim Klick auf die Notification
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent für die "Vorheriger Song"-Aktion
        val previousIntent = Intent(this, MusicService::class.java).apply {
            action = "PREVIOUS"
        }
        val previousPendingIntent = PendingIntent.getService(
            this, 1, previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent für die "Play/Pause"-Aktion
        val playPauseIntent = Intent(this, MusicService::class.java).apply {
            action = "PLAY_PAUSE"
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent für die "Nächster Song"-Aktion
        val nextIntent = Intent(this, MusicService::class.java).apply {
            action = "NEXT"
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Icon je nach Wiedergabestatus wählen (Pause-Icon wenn spielt, Play-Icon wenn pausiert)
        val playPauseIcon = if (mediaPlayer.isPlaying) {
            R.drawable.pause_24px
        } else {
            R.drawable.play_arrow_24px
        }

        // Notification zusammenbauen mit MediaStyle für Musik-Steuerung
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

    /**
     * Aktualisiert die bestehende Benachrichtigung mit dem aktuellen Wiedergabestatus
     * (z. B. nach Play/Pause-Wechsel, damit das richtige Icon angezeigt wird).
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    /**
     * Speichert den aktuellen Wiedergabestatus in SharedPreferences.
     * Gespeichert werden: Track-ID, aktuelle Position, Index in der Liste und
     * ob der Song gerade abgespielt wird. Damit kann die Wiedergabe nach einem
     * App-Neustart an der gleichen Stelle fortgesetzt werden.
     */
    private fun savePlaybackState() {
        if (currentTrack == null) {
            android.util.Log.d("MusicService", "No track to save, skipping")
            return  // Nichts speichern wenn kein Track geladen ist
        }
        val prefs = applicationContext.getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("currentTrackID", currentTrack?.audioID ?: -1)
            putInt("currentPosition", mediaPlayer.currentPosition)
            putInt("currentIndex", currentIndex)
            putBoolean("wasPlaying", mediaPlayer.isPlaying)
            commit()  // Synchron speichern statt apply(), um Datenverlust zu vermeiden
        }
        android.util.Log.d("MusicService", "SAVED: TrackID=${currentTrack?.audioID}, Position=${mediaPlayer.currentPosition}");
        val verify = prefs.getInt("currentTrackID", -1)
        android.util.Log.d("MusicService", "VERIFIED: Read back trackID=$verify")
        android.util.Log.d("MusicService", "Playback speed: ${mediaPlayer.playbackParams.speed}")

    }

    /**
     * Wird aufgerufen, wenn der Service zerstört wird.
     * Stoppt das regelmäßige Speichern, sichert den letzten Status,
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

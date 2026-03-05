package com.example.androidprojektaudioplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity

/**
 * Abstrakte Basisklasse für Activities, die an den MusicService gebunden werden.
 *
 * Kapselt die gemeinsame Logik von MainActivity und DetailActivity:
 * - ServiceConnection-Verwaltung (Binden/Entbinden)
 * - SeekBar-Update-Runnable (periodische Aktualisierung der Fortschrittsanzeige)
 * - Lautstärke-Steuerung (System-Lautstärkeregler)
 *
 * Subklassen müssen onMusicServiceConnected() implementieren, um nach dem
 * erfolgreichen Binden ihre UI-Initialisierung durchzuführen.
 */
abstract class MusicBoundActivity : AppCompatActivity() {

    /** Referenz auf den MusicService für die Steuerung der Musikwiedergabe. */
    protected var musicService: MusicService? = null

    /** Gibt an, ob die Activity aktuell an den MusicService gebunden ist. */
    protected var serviceBound = false

    /** Handler für zeitgesteuerte UI-Updates auf dem Main-Thread (SeekBar-Aktualisierung). */
    protected val handler = Handler(Looper.getMainLooper())

    /** Runnable für die SeekBar-Aktualisierung – wird in Subklassen über createSeekBarUpdater() erstellt. */
    protected var seekBarUpdater: Runnable? = null

    /**
     * ServiceConnection-Implementierung für die Kommunikation mit dem MusicService.
     * Bei erfolgreicher Verbindung wird die Service-Referenz gespeichert und
     * onMusicServiceConnected() aufgerufen.
     */
    private val serviceConnection = object : ServiceConnection {
        /** Wird aufgerufen, wenn die Verbindung zum Service hergestellt wurde. */
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            onMusicServiceConnected()
        }

        /** Wird aufgerufen, wenn die Verbindung unerwartet unterbrochen wird. */
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }

    /**
     * Wird aufgerufen, wenn die Verbindung zum MusicService hergestellt wurde.
     * Subklassen implementieren hier ihre UI-Initialisierung, Callback-Registrierung
     * und die Wiederherstellung des Wiedergabestatus.
     */
    abstract fun onMusicServiceConnected()

    /**
     * Bindet die Activity an den MusicService.
     * BIND_AUTO_CREATE erstellt den Service automatisch, falls er noch nicht läuft.
     *
     * @param alsoStart Wenn true, wird der Service zusätzlich gestartet (startService),
     *                  damit er auch nach dem Entbinden weiterläuft. Wird von der
     *                  MainActivity verwendet, damit die Musik im Hintergrund weiterläuft.
     */
    protected fun bindMusicService(alsoStart: Boolean = false) {
        Intent(this, MusicService::class.java).also { intent ->
            if (alsoStart) startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Erstellt ein Runnable, das jede Sekunde die SeekBar aktualisiert.
     * Wird über handler.post() gestartet und läuft periodisch weiter.
     *
     * @param onUpdate Optionaler Callback mit (currentPosition, duration) in Millisekunden
     *                 für zusätzliche UI-Updates (z.B. Zeitanzeige in der DetailActivity)
     * @return Das konfigurierte Runnable
     */
    protected fun createSeekBarUpdater(
        onUpdate: ((currentPosition: Int, duration: Int) -> Unit)? = null
    ): Runnable {
        return object : Runnable {
            override fun run() {
                musicService?.let {
                    if (it.mediaPlayer.isPlaying) {
                        val current = it.mediaPlayer.currentPosition
                        val duration = it.mediaPlayer.duration
                        onUpdate?.invoke(current, duration)
                    }
                }
                // Alle 1000ms (1 Sekunde) erneut ausführen
                handler.postDelayed(this, 1000)
            }
        }
    }

    /**
     * Konfiguriert einen SeekBar-Listener, der bei manueller Benutzerinteraktion
     * die Wiedergabeposition im MediaPlayer setzt (Scrubbing).
     * Reagiert nur auf Benutzer-initiierte Änderungen (fromUser = true).
     *
     * @param seekBar Die zu konfigurierende SeekBar
     */
    protected fun setupSeekBarListener(seekBar: SeekBar) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Nur bei manueller Änderung die Position setzen, nicht bei programmatischen Updates
                if (fromUser) musicService?.mediaPlayer?.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Zeigt den System-Lautstärkeregler für den Musik-Stream an.
     * Nutzt AudioManager.ADJUST_SAME, um den Regler anzuzeigen ohne die Lautstärke zu ändern.
     */
    protected fun showVolumeControl() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_SAME,      // Lautstärke nicht ändern, nur UI anzeigen
            AudioManager.FLAG_SHOW_UI       // System-Lautstärke-Overlay einblenden
        )
    }

    /**
     * Räumt beim Zerstören der Activity auf:
     * - Entfernt den SeekBar-Updater vom Handler
     * - Entbindet den Service, falls gebunden
     */
    override fun onDestroy() {
        super.onDestroy()
        seekBarUpdater?.let { handler.removeCallbacks(it) }
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}

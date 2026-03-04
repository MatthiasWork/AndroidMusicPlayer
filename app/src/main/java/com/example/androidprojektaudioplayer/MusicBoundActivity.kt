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
 * Kapselt die gemeinsame Logik von MainActivity und DetailActivity:
 * - ServiceConnection-Verwaltung
 * - SeekBar-Update-Runnable
 * - Lautstärke-Steuerung
 */
abstract class MusicBoundActivity : AppCompatActivity() {

    /** Referenz auf den MusicService für die Steuerung der Musikwiedergabe. */
    protected var musicService: MusicService? = null

    /** Gibt an, ob die Activity aktuell an den MusicService gebunden ist. */
    protected var serviceBound = false

    /** Handler für zeitgesteuerte UI-Updates (SeekBar-Aktualisierung). */
    protected val handler = Handler(Looper.getMainLooper())

    /** Runnable für die SeekBar-Aktualisierung – wird in Subklassen über createSeekBarUpdater() erstellt. */
    protected var seekBarUpdater: Runnable? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            onMusicServiceConnected()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }

    /**
     * Wird aufgerufen, wenn die Verbindung zum MusicService hergestellt wurde.
     * Subklassen implementieren hier ihre UI-Initialisierung und Callback-Registrierung.
     */
    abstract fun onMusicServiceConnected()

    /**
     * Bindet die Activity an den MusicService.
     * @param alsoStart Wenn true, wird der Service zusätzlich gestartet (für MainActivity).
     */
    protected fun bindMusicService(alsoStart: Boolean = false) {
        Intent(this, MusicService::class.java).also { intent ->
            if (alsoStart) startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Erstellt ein Runnable, das regelmäßig die SeekBar aktualisiert.
     * @param onUpdate Callback mit (currentPosition, duration) für zusätzliche UI-Updates
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
                handler.postDelayed(this, 1000)
            }
        }
    }

    /**
     * Konfiguriert einen SeekBar-Listener, der bei manueller Benutzerinteraktion
     * die Wiedergabeposition im MediaPlayer setzt.
     */
    protected fun setupSeekBarListener(seekBar: SeekBar) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.mediaPlayer?.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /** Zeigt den System-Lautstärkeregler für den Musik-Stream an. */
    protected fun showVolumeControl() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_SAME,
            AudioManager.FLAG_SHOW_UI
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        seekBarUpdater?.let { handler.removeCallbacks(it) }
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}

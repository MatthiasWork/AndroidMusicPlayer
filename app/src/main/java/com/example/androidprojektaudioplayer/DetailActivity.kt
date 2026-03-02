package com.example.androidprojektaudioplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.androidprojektaudioplayer.databinding.DetailsBinding

/**
 * Detailansicht für den aktuell spielenden Song.
 * Zeigt Titel, Künstler, Datum und einen Fortschrittsbalken an.
 * Bietet Steuerungselemente für Play/Pause, Vor/Zurück und Lautstärke.
 * Verbindet sich mit dem MusicService, um die Wiedergabe zu steuern.
 */
class DetailActivity : AppCompatActivity() {

    /** View-Binding für den Zugriff auf die UI-Elemente des Detail-Layouts. */
    private lateinit var binding: DetailsBinding

    /** Referenz auf den MusicService für die Wiedergabesteuerung. */
    private var musicService: MusicService? = null

    /** Gibt an, ob die Activity aktuell an den MusicService gebunden ist. */
    private var serviceBound = false

    /** Handler für zeitgesteuerte UI-Updates (SeekBar-Aktualisierung). */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * ServiceConnection-Implementierung für die Verbindung zum MusicService.
     * Wird aufgerufen, sobald die Bindung zum Service hergestellt oder getrennt wird.
     * Bei erfolgreicher Verbindung werden die UI-Elemente mit den aktuellen
     * Track-Daten befüllt und Callbacks für Track- und Statusänderungen registriert.
     */
    private val serviceConnection = object : ServiceConnection {
        /**
         * Wird aufgerufen, wenn die Verbindung zum MusicService hergestellt wurde.
         * Initialisiert die UI mit den aktuellen Track-Daten und registriert
         * Callbacks, damit die Anzeige bei Track- oder Statuswechsel aktualisiert wird.
         *
         * @param name  Der Komponentenname des verbundenen Service
         * @param service Der IBinder, über den der Service angesprochen werden kann
         */
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true

            // UI mit den Daten des aktuell spielenden Tracks befüllen
            musicService?.currentTrack?.let { track ->
                binding.tvDetailTitle.text = track.audioTitle
                binding.tvDetailArtist.text = track.audioArtist
                binding.tvDetailDate.text = track.audioRelDate
                val duration = musicService?.mediaPlayer?.duration ?: 0
                val current = musicService?.mediaPlayer?.currentPosition ?: 0
                binding.tvTimeCombined.text =
                    "${formatTime(current)} / ${formatTime(duration)}"
                binding.sbDetailProgress.max = duration;
                binding.sbDetailProgress.progress = current;
                // Play/Pause-Icon basierend auf dem aktuellen Wiedergabestatus setzen
                binding.btnDetailPause.setIconResource(
                    if (musicService?.mediaPlayer?.isPlaying == true) R.drawable.pause_24px
                    else R.drawable.play_arrow_24px
                )
                // SeekBar-Update starten
                handler.post(updateSeekBar)
            }

            // Callback für Trackwechsel: UI mit neuen Track-Daten aktualisieren
            musicService?.onTrackChanged = { track ->
                binding.tvDetailTitle.text = track.audioTitle
                binding.tvDetailArtist.text = track.audioArtist
                binding.tvDetailDate.text = track.audioRelDate
                val duration = musicService?.mediaPlayer?.duration ?: 0
                binding.sbDetailProgress.max = duration
                binding.tvTimeCombined.text = "0:00 / ${formatTime(duration)}"
                // SeekBar-Update neu starten
                handler.removeCallbacks(updateSeekBar)
                handler.post(updateSeekBar)
            }

            // Callback für Play/Pause-Statusänderung: Icon entsprechend aktualisieren
            musicService?.onPlayStateChanged = { isPlaying ->
                if (isPlaying) {
                    binding.btnDetailPause.setIconResource(R.drawable.pause_24px);
                } else {
                    binding.btnDetailPause.setIconResource(R.drawable.play_arrow_24px);
                }
            }
        }

        /**
         * Wird aufgerufen, wenn die Verbindung zum Service unerwartet getrennt wird.
         *
         * @param name Der Komponentenname des getrennten Service
         */
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }

    /**
     * Runnable, das die SeekBar und die Zeitanzeige jede Sekunde aktualisiert.
     * Liest die aktuelle Position aus dem MediaPlayer und aktualisiert
     * sowohl den Fortschrittsbalken als auch den Zeittext (aktuell / gesamt).
     */
    private val updateSeekBar = object : Runnable {
        override fun run() {
            musicService?.let {
                if (it.mediaPlayer.isPlaying) {
                    val current = it.mediaPlayer.currentPosition
                    val duration = it.mediaPlayer.duration
                    binding.sbDetailProgress.progress = current
                    binding.tvTimeCombined.text = "${formatTime(current)} / ${formatTime(duration)}"
                }
            }
            handler.postDelayed(this, 1000)  // Alle 1000ms (1 Sekunde) wiederholen
        }
    }

    /**
     * Wird beim Erstellen der Activity aufgerufen.
     * Initialisiert das Layout, bindet sich an den MusicService und
     * setzt die Click-Listener für alle Steuerungselemente.
     *
     * @param savedInstanceState Gespeicherter Zustand der Activity (falls vorhanden)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DetailsBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        // Edge-to-Edge: Padding für Systemleisten (Statusbar, Navigationsleiste) setzen
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailsMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // An den bereits laufenden MusicService binden (nicht neu starten)
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        // Listener: Vorheriger Song abspielen
        binding.btnDetailPrevious.setOnClickListener {
            musicService?.previous()
        }

        // Listener: Nächster Song abspielen
        binding.btnDetailNext.setOnClickListener {
            musicService?.next()
        }

        // Listener: Zwischen Play und Pause wechseln
        binding.btnDetailPause.setOnClickListener {
            musicService?.togglePlayPause()
        }

        // Listener: System-Lautstärkeregler anzeigen
        binding.btnDetailVolume.setOnClickListener {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,    // Lautstärke nicht ändern, nur UI anzeigen
                AudioManager.FLAG_SHOW_UI    // Lautstärke-Overlay einblenden
            );
        }

        // Listener: Zurück zur vorherigen Activity (MainActivity)
        binding.btnBack.setOnClickListener {
            finish();
        }

        // Listener: SeekBar-Fortschrittsänderung durch den Benutzer
        // Erlaubt es, im Song vor- und zurückzuspulen
        binding.sbDetailProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            /**
             * Wird aufgerufen, wenn sich der Fortschritt der SeekBar ändert.
             * Nur wenn der Benutzer manuell zieht (fromUser=true), wird die
             * Wiedergabeposition im MediaPlayer entsprechend gesetzt.
             */
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Wird aufgerufen, wenn die Activity zerstört wird (z. B. beim Zurücknavigieren).
     * Stoppt die SeekBar-Updates und löst die Bindung zum MusicService
     */
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBar)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    /**
     * Formatiert eine Zeitangabe in Millisekunden in das Format "M:SS"
     * (z. B. 3:05 für 185000ms).
     *
     * @param ms Die Zeit in Millisekunden
     * @return Die formatierte Zeitangabe als String im Format "M:SS"
     */
    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }
}

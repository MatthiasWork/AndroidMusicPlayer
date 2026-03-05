package com.example.androidprojektaudioplayer

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.androidprojektaudioplayer.databinding.DetailsBinding

/**
 * Detailansicht für den aktuell spielenden Song.
 * Zeigt Titel, Künstler, Datum und einen Fortschrittsbalken mit Zeitanzeige an.
 * Bietet Steuerungselemente für Play/Pause, Vor, Zurück und Lautstärke.
 *
 * Erbt von MusicBoundActivity für die gemeinsame Service-Anbindung,
 * bindet sich aber nur an den bereits laufenden Service (startet ihn nicht neu).
 */
class DetailActivity : MusicBoundActivity() {

    /** View-Binding für das Details-Layout – ersetzt findViewById-Aufrufe. */
    private lateinit var binding: DetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DetailsBinding.inflate(layoutInflater)

        // Edge-to-Edge-Darstellung aktivieren (Inhalte hinter System-Bars)
        enableEdgeToEdge()
        setContentView(binding.root)

        // System-Bar-Insets anwenden, damit Inhalte nicht von der Statusleiste verdeckt werden
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailsMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // An den bereits laufenden MusicService binden (alsoStart = false, da der Service
        // bereits von der MainActivity gestartet wurde)
        bindMusicService(alsoStart = false)

        // Listener für die Steuerungselemente registrieren
        binding.btnDetailPrevious.setOnClickListener { musicService?.previous() }
        binding.btnDetailNext.setOnClickListener { musicService?.next() }
        binding.btnDetailPause.setOnClickListener { musicService?.togglePlayPause() }
        binding.btnDetailVolume.setOnClickListener { showVolumeControl() }
        binding.btnBack.setOnClickListener { finish() }  // Zurück zur MainActivity

        // Gemeinsamer SeekBar-Listener aus der Basisklasse (ermöglicht Scrubbing)
        setupSeekBarListener(binding.sbDetailProgress)
    }

    /**
     * Wird aufgerufen, sobald die Verbindung zum MusicService steht.
     * Initialisiert die UI mit den Daten des aktuellen Tracks,
     * startet den SeekBar-Updater und registriert Callbacks für Änderungen.
     */
    override fun onMusicServiceConnected() {
        // UI mit den Daten des aktuell spielenden Tracks befüllen
        musicService?.currentTrack?.let { track ->
            updateTrackUI(track)

            // Fortschrittsbalken und Zeitanzeige initialisieren
            val duration = musicService?.mediaPlayer?.duration ?: 0
            val current = musicService?.mediaPlayer?.currentPosition ?: 0
            binding.tvTimeCombined.text = "${formatTime(current)} / ${formatTime(duration)}"
            binding.sbDetailProgress.max = duration
            binding.sbDetailProgress.progress = current

            // Play/Pause-Icon entsprechend dem aktuellen Zustand setzen
            binding.btnDetailPause.setIconResource(
                if (musicService?.mediaPlayer?.isPlaying == true) R.drawable.pause_24px
                else R.drawable.play_arrow_24px
            )
        }

        // SeekBar-Updater erstellen: Aktualisiert Fortschrittsbalken und Zeitanzeige jede Sekunde
        seekBarUpdater = createSeekBarUpdater { current, duration ->
            binding.sbDetailProgress.progress = current
            binding.tvTimeCombined.text = "${formatTime(current)} / ${formatTime(duration)}"
        }
        handler.post(seekBarUpdater!!)

        // Callback für Trackwechsel: UI mit den neuen Trackdaten aktualisieren
        musicService?.onTrackChanged = { track ->
            updateTrackUI(track)
            val duration = musicService?.mediaPlayer?.duration ?: 0
            binding.sbDetailProgress.max = duration
            binding.tvTimeCombined.text = "0:00 / ${formatTime(duration)}"
            // SeekBar-Updater neu starten, um saubere Updates zu gewährleisten
            handler.removeCallbacks(seekBarUpdater!!)
            handler.post(seekBarUpdater!!)
        }

        // Callback für Play/Pause-Statusänderung: Icon aktualisieren
        musicService?.onPlayStateChanged = { isPlaying ->
            binding.btnDetailPause.setIconResource(
                if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
            )
        }
    }

    /**
     * Aktualisiert die Textfelder der Detailansicht mit den Daten eines Tracks.
     *
     * @param track Der Track, dessen Daten angezeigt werden sollen
     */
    private fun updateTrackUI(track: myAudio) {
        binding.tvDetailTitle.text = track.audioTitle
        binding.tvDetailArtist.text = track.audioArtist
        binding.tvDetailDate.text = track.audioRelDate
    }

    /**
     * Formatiert eine Zeitangabe von Millisekunden in das Format "M:SS".
     * Beispiel: 125000ms -> "2:05"
     *
     * @param ms Zeitangabe in Millisekunden
     * @return Formatierter String im Format "M:SS"
     */
    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }
}

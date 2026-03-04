package com.example.androidprojektaudioplayer

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.androidprojektaudioplayer.databinding.DetailsBinding

/**
 * Detailansicht für den aktuell spielenden Song.
 * Zeigt Titel, Künstler, Datum und einen Fortschrittsbalken an.
 * Erbt von MusicBoundActivity für die gemeinsame Service-Anbindung.
 */
class DetailActivity : MusicBoundActivity() {

    private lateinit var binding: DetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DetailsBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailsMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // An den bereits laufenden MusicService binden (nicht neu starten)
        bindMusicService(alsoStart = false)

        // Listener für die Steuerungselemente
        binding.btnDetailPrevious.setOnClickListener { musicService?.previous() }
        binding.btnDetailNext.setOnClickListener { musicService?.next() }
        binding.btnDetailPause.setOnClickListener { musicService?.togglePlayPause() }
        binding.btnDetailVolume.setOnClickListener { showVolumeControl() }
        binding.btnBack.setOnClickListener { finish() }

        // Gemeinsamer SeekBar-Listener aus der Basisklasse
        setupSeekBarListener(binding.sbDetailProgress)
    }

    override fun onMusicServiceConnected() {
        // UI mit den Daten des aktuell spielenden Tracks befüllen
        musicService?.currentTrack?.let { track ->
            updateTrackUI(track)
            val duration = musicService?.mediaPlayer?.duration ?: 0
            val current = musicService?.mediaPlayer?.currentPosition ?: 0
            binding.tvTimeCombined.text = "${formatTime(current)} / ${formatTime(duration)}"
            binding.sbDetailProgress.max = duration
            binding.sbDetailProgress.progress = current

            binding.btnDetailPause.setIconResource(
                if (musicService?.mediaPlayer?.isPlaying == true) R.drawable.pause_24px
                else R.drawable.play_arrow_24px
            )
        }

        // SeekBar-Updater mit Zeitanzeige erstellen und starten
        seekBarUpdater = createSeekBarUpdater { current, duration ->
            binding.sbDetailProgress.progress = current
            binding.tvTimeCombined.text = "${formatTime(current)} / ${formatTime(duration)}"
        }
        handler.post(seekBarUpdater!!)

        // Callback für Trackwechsel
        musicService?.onTrackChanged = { track ->
            updateTrackUI(track)
            val duration = musicService?.mediaPlayer?.duration ?: 0
            binding.sbDetailProgress.max = duration
            binding.tvTimeCombined.text = "0:00 / ${formatTime(duration)}"
            handler.removeCallbacks(seekBarUpdater!!)
            handler.post(seekBarUpdater!!)
        }

        // Callback für Play/Pause-Statusänderung
        musicService?.onPlayStateChanged = { isPlaying ->
            binding.btnDetailPause.setIconResource(
                if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
            )
        }
    }

    /** Aktualisiert die Textfelder mit den Daten eines Tracks. */
    private fun updateTrackUI(track: myAudio) {
        binding.tvDetailTitle.text = track.audioTitle
        binding.tvDetailArtist.text = track.audioArtist
        binding.tvDetailDate.text = track.audioRelDate
    }

    /**
     * Formatiert eine Zeitangabe in Millisekunden in "M:SS".
     */
    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }
}

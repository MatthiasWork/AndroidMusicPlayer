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

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: DetailsBinding
    private var musicService: MusicService? = null
    private var serviceBound = false
    private val handler = Handler(Looper.getMainLooper())

    //Verbindung zum Service, der die Musik abspielt
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true

            // UI initial befüllen
            musicService?.currentTrack?.let { track ->
                binding.tvDetailTitle.text = track.audioTitle
                binding.tvDetailArtist.text = track.audioArtist
                binding.sbDetailProgress.max = musicService?.mediaPlayer?.duration ?: 0
                binding.tvTotalTime.text = formatTime(musicService?.mediaPlayer?.duration ?: 0)
                binding.btnDetailPause.setIconResource(
                    if (musicService?.mediaPlayer?.isPlaying == true) R.drawable.pause_24px
                    else R.drawable.play_arrow_24px
                )
                handler.post(updateSeekBar)
            }

            // Callbacks
            musicService?.onTrackChanged = { track ->
                binding.tvDetailTitle.text = track.audioTitle
                binding.tvDetailArtist.text = track.audioArtist
                binding.sbDetailProgress.max = musicService?.mediaPlayer?.duration ?: 0
                binding.tvTotalTime.text = formatTime(musicService?.mediaPlayer?.duration ?: 0)
                binding.tvCurrentTime.text = "0:00"
                handler.removeCallbacks(updateSeekBar)
                handler.post(updateSeekBar)
            }

            // Änderung des Symbols für Play/Pause
            musicService?.onPlayStateChanged = { isPlaying ->
                if (isPlaying) {
                    binding.btnDetailPause.setIconResource(R.drawable.pause_24px);
                } else {
                    binding.btnDetailPause.setIconResource(R.drawable.play_arrow_24px);
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }

    //Seekbar für den Verlauf des Songs
    private val updateSeekBar = object : Runnable {
        override fun run() {
            musicService?.let {
                if (it.mediaPlayer.isPlaying) {
                    binding.sbDetailProgress.progress = it.mediaPlayer.currentPosition
                    binding.tvCurrentTime.text = formatTime(it.mediaPlayer.currentPosition)
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

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

        // Service binden (nicht neu starten - läuft schon)
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        // Previous Button
        binding.btnDetailPrevious.setOnClickListener {
            musicService?.previous()
        }

        // Next Button
        binding.btnDetailNext.setOnClickListener {
            musicService?.next()
        }

        // Pause/Play
        binding.btnDetailPause.setOnClickListener {
            musicService?.togglePlayPause()
        }

        // Volume Button
        binding.btnDetailVolume.setOnClickListener {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI
            )
        }

        // Zurück Button
        binding.btnBack.setOnClickListener {
            finish();
        }

        // SeekBar
        binding.sbDetailProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.mediaPlayer?.seekTo(progress)
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    //Wird aufgerufen, wenn die Activity geschlossen wird
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBar)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    //Methode zur Formatierung der Zeit in Minuten und Sekunden zur Anzeige
    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
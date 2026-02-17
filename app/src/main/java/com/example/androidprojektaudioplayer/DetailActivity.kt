package com.example.androidprojektaudioplayer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.androidprojektaudioplayer.databinding.DetailsBinding

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: DetailsBinding
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var trackList: ArrayList<myAudio>
    private var currentIndex: Int = 0

    private val updateSeekBar = object : Runnable {
        override fun run() {
            if (mediaPlayer.isPlaying) {
                binding.sbDetailProgress.progress = mediaPlayer.currentPosition
                binding.tvCurrentTime.text = formatTime(mediaPlayer.currentPosition)
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

        // Daten vom Intent holen
        @Suppress("DEPRECATION")
        trackList = intent.getSerializableExtra("trackList") as? ArrayList<myAudio> ?: arrayListOf()
        currentIndex = intent.getIntExtra("currentIndex", 0)

        // Aktuellen Track abspielen
        if (trackList.isNotEmpty()) {
            playTrack(trackList[currentIndex])
        }

        // Previous Button
        binding.btnDetailPrevious.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
            } else {
                currentIndex = trackList.size - 1
            }
            playTrack(trackList[currentIndex])
        }

        // Next Button
        binding.btnDetailNext.setOnClickListener {
            if (currentIndex < trackList.size - 1) {
                currentIndex++
            } else {
                currentIndex = 0
            }
            playTrack(trackList[currentIndex])
        }

        // Pause/Play
        binding.btnDetailPause.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                binding.btnDetailPause.setIconResource(R.drawable.play_arrow_24px)
            } else {
                mediaPlayer.start()
                binding.btnDetailPause.setIconResource(R.drawable.pause_24px)
            }
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

        // SeekBar Listener
        binding.sbDetailProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun playTrack(track: myAudio) {
        mediaPlayer.reset()
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        mediaPlayer.setDataSource(this, Uri.parse(track.audioPath))
        mediaPlayer.prepare()
        mediaPlayer.start()

        // UI aktualisieren
        binding.tvDetailTitle.text = track.audioTitle
        binding.tvDetailArtist.text = track.audioArtist
        binding.sbDetailProgress.max = mediaPlayer.duration
        binding.tvTotalTime.text = formatTime(mediaPlayer.duration)
        binding.tvCurrentTime.text = "0:00"

        handler.removeCallbacks(updateSeekBar)
        handler.post(updateSeekBar)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBar)
        mediaPlayer.release()
    }

    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
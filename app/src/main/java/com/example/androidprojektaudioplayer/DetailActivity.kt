package com.example.androidprojektaudioplayer

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.androidprojektaudioplayer.databinding.ActivityMainBinding
import com.example.androidprojektaudioplayer.databinding.DetailsBinding

class DetailActivity : AppCompatActivity(){
    private lateinit var binding: DetailsBinding;
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private val handler = Handler(Looper.getMainLooper())

    private val updateSeekBar = object : Runnable {
        override fun run() {
            if (mediaPlayer.isPlaying) {
                binding.sbDetailProgress.progress = mediaPlayer.currentPosition
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        binding = DetailsBinding.inflate(layoutInflater);
        enableEdgeToEdge();
        setContentView(binding.root);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailsMain)) {
            v, insets -> val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // SeekBar Listener - wenn Nutzer die Position ändert
        binding.sbDetailProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
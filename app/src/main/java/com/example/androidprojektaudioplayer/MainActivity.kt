package com.example.androidprojektaudioplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidprojektaudioplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var myDB: DataBaseHelper
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private var currentUri: String = ""
    private var order: Boolean = false;
    private val handler = Handler(Looper.getMainLooper())

    // Runnable der die SeekBar jede Sekunde aktualisiert
    private val updateSeekBar = object : Runnable {
        override fun run() {
            if (mediaPlayer.isPlaying) {
                binding.sbProgress.progress = mediaPlayer.currentPosition
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        myDB = DataBaseHelper(this)

        // SeekBar Listener - wenn Nutzer die Position ändert
        binding.sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Nur reagieren wenn der Nutzer selbst zieht, nicht wenn der Handler aktualisiert
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 1
            )
        } else {
            ladeAudioDateien()
        }

        // Pause/Play Button
        binding.btnPause.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                binding.btnPause.setIconResource(R.drawable.play_arrow_24px)
            } else {
                mediaPlayer.start()
                binding.btnPause.setIconResource(R.drawable.pause_24px)
            }
        }

        // Volume Button
        binding.btnVolume.setOnClickListener {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI
            )
        }

        // Änderung der Sortierreihenfolge
        binding.btnChangeSortOrder.setOnClickListener {
            order = !order;
        }
    }


    //Methode um die Berechtigungen zu überprüfen
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            ladeAudioDateien()
        }
    }

    //Methode, für wenn die MainActivity wieder im Vordergrund ist
    override fun onResume() {
        super.onResume()
        ladeAudioDateien()
    }

    //Methode, um den MediaPlayer freizugeben wenn die Activity geschlossen wird
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBar)
        mediaPlayer.release()
    }

    //Methode, um eine Audiodatei abzuspielen
    fun playTrack(track: myAudio) {
        if (mediaPlayer.isPlaying && currentUri == track.audioPath) {
            return
        }
        mediaPlayer.reset()
        currentUri = track.audioPath
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        mediaPlayer.setDataSource(this, Uri.parse(track.audioPath))
        mediaPlayer.prepare()
        mediaPlayer.start()

        // SeekBar auf neuen Track setzen
        binding.sbProgress.max = mediaPlayer.duration
        binding.sbProgress.progress = 0
        handler.removeCallbacks(updateSeekBar)
        handler.post(updateSeekBar)

        binding.tvTitleText.text = track.audioTitle
        binding.tvSubTitleText.text = track.audioArtist
    }

    //Methode, um die Audiodateien zu laden
    fun ladeAudioDateien() {
        val defaultList: MutableList<myAudio> = myDB.getAllMp3Files(this) as MutableList<myAudio>
        val playLibList: MutableList<myPlaylist> = myDB.getAllPlaylistsFromDB() as MutableList<myPlaylist>;
        myDB.removeDeletedAudios(defaultList.map { it.audioID })
        for (audio in defaultList) {
            if (!myDB.audioExists(audio.audioID)) {
                myDB.addAudioToDatabase(audio)
            }
        }

        binding.rvAudioTracks.layoutManager = LinearLayoutManager(this);
        val adapter = MyAdapterAudio(defaultList, this) { track ->
            playTrack(track);
        }
        binding.rvAudioTracks.adapter = adapter;

        binding.rvPlaylists.layoutManager = LinearLayoutManager(this);
        val playListAdapter = MyAdapterPlaylist(playLibList, this);
        binding.rvPlaylists.adapter = playListAdapter;
    }
}
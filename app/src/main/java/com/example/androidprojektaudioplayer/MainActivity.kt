package com.example.androidprojektaudioplayer

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidprojektaudioplayer.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var myDB: DataBaseHelper
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private var currentUri: String = ""
    private var order: Boolean = false;
    private val handler = Handler(Looper.getMainLooper())
    private var songList: MutableList<myAudio> = mutableListOf<myAudio>();
    private var currentTrackIndex: Int = -1
    private var currentPlaylistID: Int = 1
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private var currentPathField: TextInputEditText? = null

    enum class SortOption { NAME, ARTIST, GENRE, RELEASE }

    private var currentSortOption: SortOption = SortOption.NAME

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
            ladeAudioDateien();
        }

        // Öffnen des FileExplorers für Auswahl von Datei
        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data
                    if (uri != null) {
                        // Persistente Berechtigung sichern
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        currentPathField?.setText(uri.toString())
                    }
                }
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
            if (order == false) {
                binding.btnChangeSortOrder.icon = getDrawable(R.drawable.keyboard_arrow_down_24px);
            } else {
                binding.btnChangeSortOrder.icon = getDrawable(R.drawable.keyboard_arrow_up_24px);
            }
            loadAdapter();
        }

        // Previous Button
        binding.btnPrevious.setOnClickListener {
            if (currentTrackIndex > 0) {
                playTrack(songList[currentTrackIndex - 1])
            } else {
                playTrack(songList[songList.size - 1]);
            }
        }

        // Next Button
        binding.btnNext.setOnClickListener {
            if (currentTrackIndex < songList.size - 1) {
                playTrack(songList[currentTrackIndex + 1])
            } else {
                playTrack(songList[0]);
            }
        }

        // Sortierbuttons in der RadioGroup
        binding.toggleSortOptions.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentSortOption = when (checkedId) {
                    R.id.btnSortByName -> SortOption.NAME
                    R.id.btnSortByArtist -> SortOption.ARTIST
                    R.id.btnSortByGenre -> SortOption.GENRE
                    R.id.btnSortByRelease -> SortOption.RELEASE
                    else -> SortOption.NAME
                }
                loadAdapter()
            }
        }

        // Floating action button für das Hinzufügen neuer Elemente
        binding.fabAddPlaylist.setOnClickListener {
            val bottomSheet = BottomSheetDialog(this)
            bottomSheet.behavior.isFitToContents = true;
            bottomSheet.behavior.skipCollapsed = true;
            val view = layoutInflater.inflate(R.layout.bottomsheetadd, null)

            val toggleAddOptions =
                view.findViewById<MaterialButtonToggleGroup>(R.id.toggleAddOptions)
            val layoutNewPlaylist = view.findViewById<LinearLayout>(R.id.layoutNewPlaylist)
            val layoutNewAudio = view.findViewById<LinearLayout>(R.id.layoutNewAudio)
            val etPlaylistName = view.findViewById<TextInputEditText>(R.id.etPlaylistName)
            val etAudioTitle = view.findViewById<TextInputEditText>(R.id.etAudioTitle)
            val etAudioArtist = view.findViewById<TextInputEditText>(R.id.etAudioArtist)
            val etAudioGenre = view.findViewById<TextInputEditText>(R.id.etAudioGenre)
            val etAudioDate = view.findViewById<TextInputEditText>(R.id.etAudioDate)
            val etAudioPath = view.findViewById<TextInputEditText>(R.id.etAudioPath)
            val btnBrowse = view.findViewById<MaterialButton>(R.id.btnBrowse)
            val btnSavePlaylist = view.findViewById<MaterialButton>(R.id.btnSavePlaylist)
            val btnSaveAudio = view.findViewById<MaterialButton>(R.id.btnSaveAudio)

            // Toggle zwischen Playlist und Audio
            toggleAddOptions.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    when (checkedId) {
                        R.id.btnAddPlaylist -> {
                            layoutNewPlaylist.visibility = View.VISIBLE
                            layoutNewAudio.visibility = View.GONE
                        }

                        R.id.btnAddAudio -> {
                            layoutNewPlaylist.visibility = View.GONE
                            layoutNewAudio.visibility = View.VISIBLE
                        }
                    }
                }
            }

            // File Explorer öffnen
            btnBrowse.setOnClickListener {
                currentPathField = etAudioPath
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "audio/*"
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                filePickerLauncher.launch(intent)
            }

            // Playlist speichern
            btnSavePlaylist.setOnClickListener {
                val name = etPlaylistName.text.toString()
                if (name.isNotEmpty()) {
                    val playlist = myPlaylist()
                    playlist.playlistID = myDB.getNextAvailablePlaylistID()
                    playlist.playlistTitle = name
                    myDB.addPlaylistToDatabase(playlist)
                    ladeAudioDateien()
                    bottomSheet.dismiss()
                }
            }

            view.findViewById<TextInputLayout>(R.id.tilAudioDate).setEndIconOnClickListener {
                val calendar = java.util.Calendar.getInstance()
                DatePickerDialog(
                    this,
                    { _, year, month, day ->
                        val formattedDate = String.format("%02d.%02d.%04d", day, month + 1, year)
                        etAudioDate.setText(formattedDate)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            // Audio speichern
            btnSaveAudio.setOnClickListener {
                val title = etAudioTitle.text.toString()
                val artist = etAudioArtist.text.toString()
                val genre = etAudioGenre.text.toString()
                val date = etAudioDate.text.toString()
                val path = etAudioPath.text.toString()

                if (title.isNotEmpty() && path.isNotEmpty()) {
                    val audio = myAudio()
                    audio.audioID = myDB.getNextAvailableAudioID()
                    audio.audioTitle = title
                    audio.audioArtist = artist
                    audio.audioGenre = genre
                    audio.audioRelDate = date
                    audio.audioPath = path
                    myDB.addAudioToDatabase(audio);
                    myDB.addAudioToPlaylist(audio.audioID, 1)
                    ladeAudioDateien()
                    bottomSheet.dismiss()
                }
            }

            bottomSheet.setContentView(view)
            bottomSheet.show()
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
        if (songList.isEmpty()) {
            ladeAudioDateien();
        }
    }

    //Methode, um den MediaPlayer freizugeben wenn die Activity geschlossen wird
    override fun onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBar);
        mediaPlayer.release();
    }

    //Methode, um eine Audiodatei abzuspielen
    fun playTrack(track: myAudio) {
        if (mediaPlayer.isPlaying && currentUri == track.audioPath) {
            return
        }
        currentTrackIndex = songList.indexOf(track)
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

    //Methode, um den Adapter zu laden und zuzuweisen
    fun loadAdapter() {
        when (currentSortOption) {
            SortOption.NAME -> if (order) songList.sortByDescending { it.audioTitle } else songList.sortBy { it.audioTitle }
            SortOption.ARTIST -> if (order) songList.sortByDescending { it.audioArtist } else songList.sortBy { it.audioArtist }
            SortOption.GENRE -> if (order) songList.sortByDescending { it.audioGenre } else songList.sortBy { it.audioGenre }
            SortOption.RELEASE -> {
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                if (order) {
                    songList.sortByDescending { track ->
                        try {
                            sdf.parse(track.audioRelDate)
                        } catch (e: Exception) {
                            null
                        }
                    }
                } else {
                    songList.sortBy { track ->
                        try {
                            sdf.parse(track.audioRelDate)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        }

        binding.rvAudioTracks.layoutManager = LinearLayoutManager(this)
        val adapter = MyAdapterAudio(
            songList, this,
            onTrackClicked = { track -> playTrack(track); },
            onTrackDeleted = { track ->
                myDB.deleteAudioEntry(track);
                ladeAudioDateien();
            },
            onTrackEdited = { track ->
                myDB.editAudioEntry(track);
                ladeAudioDateien();
            },
            onAddToPlaylist = { track ->
                //myDB.addAudioToPlaylist(track.audioID);
                ladeAudioDateien();
            },
            onRemoveFromPlaylist = { track ->
            }
        )
        binding.rvAudioTracks.adapter = adapter
    }

    //Methode, um die Audiodateien zu laden
    fun ladeAudioDateien() {
        val mediaList = myDB.getAllMp3Files(this) as MutableList<myAudio>
        myDB.removeDeletedAudios(mediaList.map { it.audioID })
        for (audio in mediaList) {
            if (!myDB.audioExists(audio.audioID)) {
                myDB.addAudioToDatabase(audio);
                myDB.addAudioToPlaylist(audio.audioID, 1);
            }
        }

        songList = myDB.getAudiosByPlaylist(currentPlaylistID) as MutableList<myAudio>

        val playLibList: MutableList<myPlaylist> =
            myDB.getAllPlaylistsFromDB() as MutableList<myPlaylist>
        binding.rvPlaylists.layoutManager = LinearLayoutManager(this)
        val playListAdapter = MyAdapterPlaylist(
            playLibList, this,
            onPlaylistClicked = { playlist ->
                currentPlaylistID = playlist.playlistID
                songList = myDB.getAudiosByPlaylist(playlist.playlistID) as MutableList<myAudio>
                loadAdapter()
            },
            onPlaylistEdited = { playlist, newName ->
                playlist.playlistTitle = newName
                myDB.editPlaylistEntry(playlist)
                ladeAudioDateien()
            },
            onPlaylistDeleted = { playlist ->
                myDB.deletePlaylistEntry(playlist)
                ladeAudioDateien()
            }
        )
        binding.rvPlaylists.adapter = playListAdapter

        loadAdapter()
    }
}
package com.example.androidprojektaudioplayer

import android.Manifest
import android.app.DatePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
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
import androidx.recyclerview.widget.RecyclerView
import com.example.androidprojektaudioplayer.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding;
    private lateinit var myDB: DataBaseHelper;
    private var musicService: MusicService? = null;
    private var serviceBound = false;
    private var order: Boolean = false;
    private val handler = Handler(Looper.getMainLooper());
    private var songList: MutableList<myAudio> = mutableListOf();
    private var currentPlaylistID: Int = 1;
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>;
    private var currentPathField: TextInputEditText? = null;

    //Enum für die Sortierungsoptionen
    enum class SortOption { NAME, ARTIST, GENRE, RELEASE };
    private var currentSortOption: SortOption = SortOption.NAME;

    //Verbindung zum Service, der die Musik abspielt
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true

            // Callbacks registrieren
            musicService?.onTrackChanged = { track ->
                binding.tvTitleText.text = track.audioTitle
                binding.tvSubTitleText.text = track.audioArtist
                binding.sbProgress.max = musicService?.mediaPlayer?.duration ?: 0
                binding.sbProgress.progress = 0
                handler.removeCallbacks(updateSeekBar)
                handler.post(updateSeekBar)
            }

            musicService?.onPlayStateChanged = { isPlaying ->
                binding.btnPause.setIconResource(
                    if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
                )
            }

            // HIER HINZUFÜGEN
            restorePlaybackState()
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
                    binding.sbProgress.progress = it.mediaPlayer.currentPosition
                }
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

        android.util.Log.d("MainActivity", "Database cleared!")

        // Service starten und binden
        Intent(this, MusicService::class.java).also { intent ->
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        // Suchleiste Listener
        binding.svSearch.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterSongs(newText ?: "")
                return true
            }
        })

        // SeekBar Listener
        binding.sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.mediaPlayer?.seekTo(progress)
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

        // File Picker
        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data
                    if (uri != null) {
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
            musicService?.togglePlayPause()
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

        // Sort Order Button
        binding.btnChangeSortOrder.setOnClickListener {
            order = !order
            binding.btnChangeSortOrder.icon = getDrawable(
                if (order) R.drawable.keyboard_arrow_up_24px
                else R.drawable.keyboard_arrow_down_24px
            )
            loadAdapter()
        }

        // Previous Button
        binding.btnPrevious.setOnClickListener {
            musicService?.previous()
        }

        // Next Button
        binding.btnNext.setOnClickListener {
            musicService?.next()
        }

        // Sort Toggle
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

        // FAB
        binding.fabAddPlaylist.setOnClickListener {
            val bottomSheet = BottomSheetDialog(this)
            bottomSheet.behavior.isFitToContents = true
            bottomSheet.behavior.skipCollapsed = true
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
                val calendar = Calendar.getInstance()
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
                    myDB.addAudioToDatabase(audio)
                    myDB.addAudioToPlaylist(audio.audioID, 1)
                    ladeAudioDateien()
                    bottomSheet.dismiss()
                }
            }

            bottomSheet.setContentView(view)
            bottomSheet.show()
        }

        // Card für Detailansicht
        binding.cardOpenDetail.setOnClickListener {
            val intent = Intent(this, DetailActivity::class.java)
            startActivity(intent)
        }

        // Settings Button - Ordnerauswahl
        binding.btnSettings.setOnClickListener {
            val bottomSheet = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.musicfolderlib_holder, null)  // Du musst dieses Layout noch erstellen

            val folders = myDB.getAllAudioFolders(this) as MutableList<String>
            val folderAdapter = MyAdapterFolder(folders, this)

            // Aktuell ausgewählte Ordner aus SharedPreferences laden
            val prefs = applicationContext.getSharedPreferences("AppSettings", MODE_PRIVATE)
            val savedFolders = prefs.getStringSet("selectedFolders", null)

            if (savedFolders != null) {
                folderAdapter.selectedFolders.addAll(savedFolders)
            } else {
                // Wenn nichts gespeichert, alle Ordner vorauswählen
                folderAdapter.selectedFolders.addAll(folders)
            }

            view.findViewById<RecyclerView>(R.id.rvFolderSelection).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = folderAdapter
            }

            view.findViewById<MaterialButton>(R.id.btnConfirmFolderChanges).setOnClickListener {
                // Auswahl speichern
                prefs.edit()
                    .putStringSet("selectedFolders", folderAdapter.selectedFolders)
                    .apply()

                // Songs neu laden mit Filter
                ladeAudioDateien()
                bottomSheet.dismiss()
            }

            bottomSheet.setContentView(view)
            bottomSheet.show()
        }


    }

    //Methode zum fragen nach der Berechtigung
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

    //Wird aufgerufen, wenn die Activity im Fokus ist
    override fun onResume() {
        super.onResume()
        if (songList.isEmpty()) {
            ladeAudioDateien()
        }

        // UI aktualisieren wenn Service bereits läuft
        if (serviceBound) {
            restorePlaybackState()
        }
    }

    //Wird aufgerufen, wenn die Activity geschlossen wird
    override fun onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBar);
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private fun restorePlaybackState() {
        musicService?.let { service ->
            // Wenn Service bereits einen Track hat, UI aktualisieren
            if (service.currentTrack != null) {
                service.currentTrack?.let { track ->
                    binding.tvTitleText.text = track.audioTitle
                    binding.tvSubTitleText.text = track.audioArtist

                    try {
                        if (service.mediaPlayer.duration > 0) {
                            binding.sbProgress.max = service.mediaPlayer.duration
                            binding.sbProgress.progress = service.mediaPlayer.currentPosition
                        }
                    } catch (e: Exception) {

                    }

                    binding.btnPause.setIconResource(
                        if (service.mediaPlayer.isPlaying) {
                            R.drawable.pause_24px
                        } else {
                            R.drawable.play_arrow_24px
                        }
                    )
                    handler.post(updateSeekBar)
                }
            } else {
                val prefs = applicationContext.getSharedPreferences("MusicPlayerPrefs", MODE_PRIVATE)
                val trackID = prefs.getInt("currentTrackID", -1)
                val position = prefs.getInt("currentPosition", 0)
                android.util.Log.d("MainActivity", "LOADED: TrackID=$trackID, Position=$position, SongListSize=${songList.size}")
                val wasPlaying = prefs.getBoolean("wasPlaying", false)
                if (trackID == -1 || songList.isEmpty()) return

                val track = songList.find { it.audioID == trackID } ?: return
                val index = songList.indexOf(track)
                if (index == -1) return

                service.trackList = songList
                service.playTrack(track, index)

                handler.postDelayed({
                    try {
                        service.mediaPlayer.seekTo(position)
                        if (!wasPlaying) service.pause()
                    } catch (e: Exception) {
                    }
                }, 0)
            }
        }
    }

    //Methode zur Suche nach einem Song mit dem Inhalt aus der Suche
    private fun filterSongs(query: String) {
        val allSongs = myDB.getAudiosByPlaylist(currentPlaylistID) as MutableList<myAudio>

        songList = if (query.isEmpty()) {
            allSongs
        } else {
            allSongs.filter { audio ->
                audio.audioTitle.contains(query, ignoreCase = true) ||
                        audio.audioArtist.contains(query, ignoreCase = true) ||
                        audio.audioGenre.contains(query, ignoreCase = true)
            }.toMutableList()
        }

        loadAdapter()
    }

    //Methode zum Abspielen einer Audiodatei
    fun playTrack(track: myAudio) {
        musicService?.let {
            it.trackList = songList;
            val index = songList.indexOf(track);
            it.playTrack(track, index);
        }
    }

    //Methode zum Laden aller Adapter
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
            songList, this, currentPlaylistID,
            onTrackClicked = { track -> playTrack(track) },
            onTrackEdited = { track ->
                myDB.editAudioEntry(track)
                ladeAudioDateien()
            },
            onAddToPlaylist = { track ->
                val bottomSheet = BottomSheetDialog(this)
                val view = layoutInflater.inflate(R.layout.playlist_selectorholder, null)

                val playlists = myDB.getAllPlaylistsFromDB()
                    .filter { it.playlistID != 1 }
                    .toMutableList()

                val selectionAdapter = MyAdapterPlaylistSelect(playlists)
                val existingPlaylists = myDB.getPlaylistIDsForAudio(track.audioID)
                selectionAdapter.selectedPlaylists.addAll(existingPlaylists)

                view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvPlaylistSelection)
                    .apply {
                        layoutManager = LinearLayoutManager(this@MainActivity)
                        adapter = selectionAdapter
                    }

                view.findViewById<MaterialButton>(R.id.btnConfirmAddToPlaylist).setOnClickListener {
                    for (playlistID in selectionAdapter.selectedPlaylists) {
                        if (!existingPlaylists.contains(playlistID)) {
                            myDB.addAudioToPlaylist(track.audioID, playlistID)
                        }
                    }
                    for (playlistID in existingPlaylists) {
                        if (!selectionAdapter.selectedPlaylists.contains(playlistID)) {
                            myDB.removeAudioFromPlaylist(track.audioID, playlistID)
                        }
                    }
                    ladeAudioDateien()
                    bottomSheet.dismiss()
                }

                bottomSheet.setContentView(view)
                bottomSheet.show()
            },
            onRemoveFromPlaylist = { track ->
                myDB.removeAudioFromPlaylist(track.audioID, currentPlaylistID)
                ladeAudioDateien()
            }
        )
        binding.rvAudioTracks.adapter = adapter
    }

    //Methode zum Laden aller Audiodateien und verbinden von recyclerView mit den Listen von Audios und Playlists
    fun ladeAudioDateien() {
        val mediaList = myDB.getAllMp3Files(this) as MutableList<myAudio>
        myDB.removeDeletedAudios(mediaList.map { it.audioID })
        for (audio in mediaList) {
            if (!myDB.audioExists(audio.audioID)) {
                myDB.addAudioToDatabase(audio)
                myDB.addAudioToPlaylist(audio.audioID, 1)
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
        loadAdapter();
    }
}
package com.example.androidprojektaudioplayer

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.app.AppCompatDelegate
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

/**
 * Hauptactivity der Audioplayer-App.
 * Zeigt die Song-Liste und Playlists an, bietet Steuerungselemente für die Wiedergabe,
 * eine Suchfunktion, Sortiermöglichkeiten und Dialoge zum Hinzufügen.
 * Erbt von MusicBoundActivity für die gemeinsame Service-Anbindung.
 */
class MainActivity : MusicBoundActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var myDB: DataBaseHelper

    /** Sortierreihenfolge: false = aufsteigend, true = absteigend. */
    private var order: Boolean = false

    /** Die aktuell angezeigte Song-Liste (kann gefiltert und sortiert sein). */
    private var songList: MutableList<myAudio> = mutableListOf()

    /** ID der aktuell ausgewählten Playlist (1 = Standard-Playlist "Alle"). */
    private var currentPlaylistID: Int = 1

    /** Launcher für den Datei-Picker zum Hinzufügen von Audiodateien. */
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>

    /** Referenz auf das Pfad-Eingabefeld im Add-Dialog für den File-Picker. */
    private var currentPathField: TextInputEditText? = null

    enum class SortOption { NAME, ARTIST, GENRE, RELEASE }
    private var currentSortOption: SortOption = SortOption.NAME

    /**
     * Moderner Permission-Launcher (ersetzt deprecated onRequestPermissionsResult).
     * Lädt die Audiodateien, sobald die Audio-Berechtigung gewährt wird.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_MEDIA_AUDIO] == true) {
            ladeAudioDateien()
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

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        myDB = DataBaseHelper(this)

        // MusicService starten und binden (über Basisklasse)
        bindMusicService(alsoStart = true)

        // Fokus von der Suchleiste entfernen bei Hintergrund-Touch
        binding.main.setOnTouchListener { _, _ ->
            binding.svSearch.clearFocus()
            false
        }

        setupSearchView()
        setupSeekBarListener(binding.sbProgress)
        requestPermissions()
        setupFilePickerLauncher()
        setupPlayerControls()
        setupSortControls()
        setupFabAddDialog()
        setupSettingsButton()

        // Detailansicht öffnen
        binding.cardOpenDetail.setOnClickListener {
            startActivity(Intent(this, DetailActivity::class.java))
        }
    }

    override fun onMusicServiceConnected() {
        // Callback: Bei Trackwechsel die UI aktualisieren
        musicService?.onTrackChanged = { track ->
            binding.tvTitleText.text = track.audioTitle
            binding.tvSubTitleText.text = track.audioArtist
            binding.sbProgress.max = musicService?.mediaPlayer?.duration ?: 0
            binding.sbProgress.progress = 0
            seekBarUpdater?.let { handler.removeCallbacks(it) }
            seekBarUpdater?.let { handler.post(it) }
        }

        // Callback: Bei Play/Pause-Wechsel das Icon aktualisieren
        musicService?.onPlayStateChanged = { isPlaying ->
            binding.btnPause.setIconResource(
                if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
            )
        }

        // SeekBar-Updater erstellen
        seekBarUpdater = createSeekBarUpdater { current, _ ->
            binding.sbProgress.progress = current
        }

        restorePlaybackState()
    }

    override fun onResume() {
        super.onResume()
        ladeAudioDateien()
        if (serviceBound) restorePlaybackState()
    }

    //region Setup-Methoden

    /** Konfiguriert die Suchleiste mit Listener und Farbgebung. */
    private fun setupSearchView() {
        binding.svSearch.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterSongs(newText ?: "")
                return true
            }
        })

        binding.svSearch.apply {
            findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.apply {
                setTextColor(ContextCompat.getColor(context, R.color.primary_accent))
                setHintTextColor(ContextCompat.getColor(context, R.color.subtext))
            }
            findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)?.apply {
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary_accent))
            }
            findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)?.apply {
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary_accent))
            }
        }
    }

    /** Prüft und fordert nötige Berechtigungen über den modernen Permission-Launcher an. */
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            ladeAudioDateien()
        }
    }

    /** Registriert den File-Picker für das Hinzufügen von Audiodateien. */
    private fun setupFilePickerLauncher() {
        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data ?: return@registerForActivityResult
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    currentPathField?.setText(uri.toString())

                    // Metadaten aus der Audiodatei auslesen
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(this, uri)

                        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            ?: uri.lastPathSegment ?: "Unbekannt"
                        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            ?: "Unbekannt"
                        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                            ?: "Unbekannt"
                        val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                            ?: ""

                        val rootView = currentPathField?.rootView
                        rootView?.findViewById<TextInputEditText>(R.id.etAudioTitle)?.setText(title)
                        rootView?.findViewById<TextInputEditText>(R.id.etAudioArtist)?.setText(artist)
                        rootView?.findViewById<TextInputEditText>(R.id.etAudioGenre)?.setText(album)
                        rootView?.findViewById<TextInputEditText>(R.id.etAudioDate)?.setText(date)
                        retriever.release()
                    } catch (e: Exception) {
                        Log.e("MainActivity-Metadaten", "Error reading metadata: $e")
                    }
                }
            }
    }

    /** Konfiguriert die Player-Steuerungselemente. */
    private fun setupPlayerControls() {
        binding.btnPause.setOnClickListener { musicService?.togglePlayPause() }
        binding.btnVolume.setOnClickListener { showVolumeControl() }
        binding.btnPrevious.setOnClickListener { musicService?.previous() }
        binding.btnNext.setOnClickListener { musicService?.next() }
    }

    /** Konfiguriert die Sortier-Toggle-Buttons und Reihenfolge-Button. */
    private fun setupSortControls() {
        binding.btnChangeSortOrder.setOnClickListener {
            order = !order
            binding.btnChangeSortOrder.icon = AppCompatResources.getDrawable(
                this,
                if (order) R.drawable.keyboard_arrow_up_24px else R.drawable.keyboard_arrow_down_24px
            )
            loadAdapter()
        }

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
    }

    /** Konfiguriert den FAB-Dialog zum Hinzufügen von Playlists oder Audiodateien. */
    private fun setupFabAddDialog() {
        binding.fabAddPlaylist.setOnClickListener {
            val bottomSheet = BottomSheetDialog(this)
            bottomSheet.behavior.isFitToContents = true
            bottomSheet.behavior.skipCollapsed = true
            bottomSheet.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundResource(android.R.color.transparent)
            val view = layoutInflater.inflate(R.layout.bottomsheetadd, null)

            val toggleAddOptions = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleAddOptions)
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

            // Toggle zwischen Playlist- und Audio-Formular
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

            // Datei-Browser öffnen
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

            // Neue Playlist speichern
            btnSavePlaylist.setOnClickListener {
                val name = etPlaylistName.text.toString()
                if (name.isNotEmpty()) {
                    val playlist = myPlaylist(
                        playlistID = myDB.getNextAvailablePlaylistID(),
                        playlistTitle = name
                    )
                    myDB.addPlaylistToDatabase(playlist)
                    ladeAudioDateien()
                    bottomSheet.dismiss()
                }
            }

            // DatePicker über die gemeinsame Hilfsmethode
            view.findViewById<TextInputLayout>(R.id.tilAudioDate).setEndIconOnClickListener {
                DatePickerUtils.showDatePicker(this, etAudioDate)
            }

            // Neuen Audiotitel speichern
            btnSaveAudio.setOnClickListener {
                val title = etAudioTitle.text.toString()
                val path = etAudioPath.text.toString()

                if (title.isNotEmpty() && path.isNotEmpty()) {
                    val audio = myAudio(
                        audioID = myDB.getNextAvailableAudioID(),
                        audioTitle = title,
                        audioArtist = etAudioArtist.text.toString(),
                        audioGenre = etAudioGenre.text.toString(),
                        audioRelDate = etAudioDate.text.toString(),
                        audioPath = path
                    )
                    myDB.addAudioToDatabase(audio)
                    myDB.addAudioToPlaylist(audio.audioID, 1)
                    ladeAudioDateien()
                    bottomSheet.dismiss()
                }
            }

            bottomSheet.setContentView(view)
            bottomSheet.show()
        }
    }

    /** Konfiguriert den Einstellungs-Button für die Ordnerauswahl. */
    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            val bottomSheet = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.musicfolderlib_holder, null)

            val folders = myDB.getAllAudioFolders(this) as MutableList<String>
            val folderAdapter = MyAdapterFolder(folders, this)

            val prefs = applicationContext.getSharedPreferences("AppSettings", MODE_PRIVATE)
            val savedFolders = prefs.getStringSet("selectedFolders", null)

            if (savedFolders != null) {
                folderAdapter.selectedFolders.addAll(savedFolders)
            } else {
                folderAdapter.selectedFolders.addAll(folders)
            }

            view.findViewById<RecyclerView>(R.id.rvFolderSelection).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = folderAdapter
            }

            view.findViewById<MaterialButton>(R.id.btnConfirmFolderChanges).setOnClickListener {
                prefs.edit()
                    .putStringSet("selectedFolders", folderAdapter.selectedFolders)
                    .apply()
                ladeAudioDateien()
                bottomSheet.dismiss()
            }

            bottomSheet.setContentView(view)
            bottomSheet.show()
        }
    }

    //endregion

    //region Wiedergabe und Daten

    /**
     * Stellt den Wiedergabestatus aus dem MusicService oder SharedPreferences wieder her.
     */
    private fun restorePlaybackState() {
        musicService?.let { service ->
            if (service.currentTrack != null) {
                service.currentTrack?.let { track ->
                    binding.tvTitleText.text = track.audioTitle
                    binding.tvSubTitleText.text = track.audioArtist

                    try {
                        if (service.mediaPlayer.duration > 0) {
                            binding.sbProgress.max = service.mediaPlayer.duration
                            binding.sbProgress.progress = service.mediaPlayer.currentPosition
                        }
                    } catch (_: Exception) {}

                    binding.btnPause.setIconResource(
                        if (service.mediaPlayer.isPlaying) R.drawable.pause_24px
                        else R.drawable.play_arrow_24px
                    )
                    seekBarUpdater?.let { handler.post(it) }
                }
            } else {
                // Letzten Zustand aus SharedPreferences laden
                val prefs = applicationContext.getSharedPreferences("MusicPlayerPrefs", MODE_PRIVATE)
                val trackID = prefs.getInt("currentTrackID", -1)
                val position = prefs.getInt("currentPosition", 0)
                if (trackID == -1 || songList.isEmpty()) return

                val track = songList.find { it.audioID == trackID } ?: return
                val index = songList.indexOf(track)
                if (index == -1) return

                service.trackList = songList
                service.loadTrack(track, index)

                try {
                    service.mediaPlayer.seekTo(position)
                    binding.sbProgress.progress = position
                    binding.btnPause.setIconResource(R.drawable.play_arrow_24px)
                } catch (_: Exception) {}
            }
        }
    }

    /** Filtert die Song-Liste anhand des Suchtextes (Titel, Künstler, Genre). */
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

    /** Startet die Wiedergabe eines Tracks über den MusicService. */
    fun playTrack(track: myAudio) {
        musicService?.let {
            it.trackList = songList
            it.playTrack(track, songList.indexOf(track))
        }
    }

    /** Sortiert die Song-Liste und aktualisiert den RecyclerView-Adapter. */
    fun loadAdapter() {
        when (currentSortOption) {
            SortOption.NAME -> if (order) songList.sortByDescending { it.audioTitle } else songList.sortBy { it.audioTitle }
            SortOption.ARTIST -> if (order) songList.sortByDescending { it.audioArtist } else songList.sortBy { it.audioArtist }
            SortOption.GENRE -> if (order) songList.sortByDescending { it.audioGenre } else songList.sortBy { it.audioGenre }
            SortOption.RELEASE -> {
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                val parseDate = { track: myAudio ->
                    try { sdf.parse(track.audioRelDate) } catch (_: Exception) { null }
                }
                if (order) songList.sortByDescending(parseDate) else songList.sortBy(parseDate)
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
            onAddToPlaylist = { track -> showPlaylistSelector(track) },
            onRemoveFromPlaylist = { track ->
                myDB.removeAudioFromPlaylist(track.audioID, currentPlaylistID)
                ladeAudioDateien()
            }
        )
        binding.rvAudioTracks.adapter = adapter
    }

    /** Zeigt das BottomSheet zur Playlist-Auswahl für einen Song an. */
    private fun showPlaylistSelector(track: myAudio) {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.playlist_selectorholder, null)

        val playlists = myDB.getAllPlaylistsFromDB()
            .filter { it.playlistID != 1 }
            .toMutableList()

        val selectionAdapter = MyAdapterPlaylistSelect(playlists)
        val existingPlaylists = myDB.getPlaylistIDsForAudio(track.audioID)
        selectionAdapter.selectedPlaylists.addAll(existingPlaylists)

        view.findViewById<RecyclerView>(R.id.rvPlaylistSelection).apply {
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
    }

    /**
     * Hauptmethode zum Laden und Synchronisieren aller Audiodateien und Playlists.
     */
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

        // Playlist-RecyclerView aktualisieren
        val playLibList = myDB.getAllPlaylistsFromDB() as MutableList<myPlaylist>
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

    //endregion
}

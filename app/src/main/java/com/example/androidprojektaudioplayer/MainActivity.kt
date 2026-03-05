package com.example.androidprojektaudioplayer

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
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
import java.time.LocalDate
import java.util.Calendar

/**
 * Hauptactivity der Audioplayer-App "Soundio".
 *
 * Funktionsumfang:
 * - Anzeige der Song-Liste in einem RecyclerView mit Sortier- und Suchfunktion
 * - Horizontale Playlist-Leiste zur Auswahl der aktiven Playlist
 * - Player-Steuerungselemente (Play/Pause, Vor, Zurück, Lautstärke) am unteren Rand
 * - BottomSheet-Dialog zum Hinzufügen neuer Playlists oder Audiodateien
 * - Einstellungsdialog zur Ordnerauswahl (welche Musikordner angezeigt werden)
 * - Detailansicht für den aktuell spielenden Song
 *
 * Erbt von MusicBoundActivity für die gemeinsame Service-Anbindung an den MusicService.
 */
class MainActivity : MusicBoundActivity() {

    /** View-Binding für das Hauptlayout – ersetzt alle findViewById-Aufrufe. */
    private lateinit var binding: ActivityMainBinding

    /** Datenbank-Helfer für alle CRUD-Operationen auf Songs und Playlists. */
    private lateinit var myDB: DataBaseHelper

    /** Sortierreihenfolge: false = aufsteigend (A-Z), true = absteigend (Z-A). */
    private var order: Boolean = false

    /** Die aktuell angezeigte Song-Liste (kann durch Suche und Sortierung gefiltert sein). */
    private var songList: MutableList<myAudio> = mutableListOf()

    /** ID der aktuell ausgewählten Playlist (1 = Standard-Playlist "Alle"). */
    private var currentPlaylistID: Int = 1

    /** ActivityResultLauncher für den System-Datei-Picker zum Hinzufügen von Audiodateien. */
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>

    /** Referenz auf das Pfad-Eingabefeld im Add-Dialog – wird vom File-Picker beschrieben. */
    private var currentPathField: TextInputEditText? = null

    /** Enum für die verfügbaren Sortieroptionen. */
    enum class SortOption { NAME, ARTIST, GENRE, RELEASE }

    /** Die aktuell gewählte Sortieroption (Standard: nach Name). */
    private var currentSortOption: SortOption = SortOption.NAME

    /**
     * Moderner Permission-Launcher (ersetzt das deprecated onRequestPermissionsResult).
     * Wird mit registerForActivityResult registriert und verarbeitet die Berechtigungsantwort.
     * Lädt die Audiodateien, sobald die Audio-Berechtigung gewährt wird.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_MEDIA_AUDIO] == true) {
            loadAudioFiles()
        }
    }

    /**
     * Initialisiert die Activity: Layout, Datenbank, Service-Anbindung und alle UI-Komponenten.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        // Edge-to-Edge-Darstellung aktivieren (Inhalte hinter System-Bars)
        enableEdgeToEdge()
        setContentView(binding.root)

        // System-Bar-Insets anwenden, damit Inhalte nicht verdeckt werden
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Dark Mode deaktivieren – die App nutzt ein eigenes Farbschema
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Datenbank-Helfer initialisieren
        myDB = DataBaseHelper(this)

        // MusicService starten UND binden (alsoStart = true, damit er im Hintergrund weiterläuft)
        bindMusicService(alsoStart = true)

        // Bei Touch auf den Hintergrund: Fokus von der Suchleiste entfernen (Keyboard schließen)
        binding.main.setOnTouchListener { _, _ ->
            binding.svSearch.clearFocus()
            false  // Event nicht konsumieren, damit andere Views es auch verarbeiten können
        }

        // Alle UI-Komponenten initialisieren
        setupSearchView();
        setupSeekBarListener(binding.sbProgress);
        requestPermissions();
        setupFilePickerLauncher();
        setupPlayerControls();
        setupSortControls();
        setupFabAddDialog();
        setupSettingsButton();

        // Klick auf die Player-Card: Detailansicht öffnen
        binding.cardOpenDetail.setOnClickListener {
            startActivity(Intent(this, DetailActivity::class.java))
        }
    }

    /**
     * Wird aufgerufen, sobald die Verbindung zum MusicService steht.
     * Registriert Callbacks für Track- und Statusänderungen,
     * erstellt den SeekBar-Updater und stellt den letzten Wiedergabestatus wieder her.
     */
    override fun onMusicServiceConnected() {
        // Callback: Bei Trackwechsel die Player-UI in der unteren Leiste aktualisieren
        musicService?.onTrackChanged = { track ->
            binding.tvTitleText.text = track.audioTitle
            binding.tvSubTitleText.text = track.audioArtist
            binding.sbProgress.max = musicService?.mediaPlayer?.duration ?: 0
            binding.sbProgress.progress = 0
            // SeekBar-Updater neu starten
            seekBarUpdater?.let { handler.removeCallbacks(it) }
            seekBarUpdater?.let { handler.post(it) }
        }

        // Callback: Bei Play/Pause-Wechsel das Icon in der Player-Leiste aktualisieren
        musicService?.onPlayStateChanged = { isPlaying ->
            binding.btnPause.setIconResource(
                if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
            )
        }

        // SeekBar-Updater erstellen: Aktualisiert den Fortschrittsbalken jede Sekunde
        seekBarUpdater = createSeekBarUpdater { current, _ ->
            binding.sbProgress.progress = current
        }

        // Letzten Wiedergabestatus wiederherstellen (aus Service oder SharedPreferences)
        restorePlaybackState()
    }

    /**
     * Wird bei jedem Zurückkehren zur Activity aufgerufen.
     * Lädt die Audiodateien neu (für den Fall, dass sich etwas geändert hat)
     * und stellt den Wiedergabestatus wieder her.
     */
    override fun onResume() {
        super.onResume()
        loadAudioFiles()
        if (serviceBound) restorePlaybackState()
    }

    //region Setup-Methoden

    /**
     * Konfiguriert die Suchleiste (SearchView) mit einem TextChange-Listener
     * und passt die Farben an das App-Design an.
     */
    private fun setupSearchView() {
        // Listener für Texteingabe: Filtert die Song-Liste bei jeder Änderung
        binding.svSearch.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterSongs(newText ?: "")
                return true
            }
        })

        // Farben der SearchView-Elemente an das App-Design anpassen
        binding.svSearch.apply {
            // Textfarbe und Hint-Farbe setzen
            findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.apply {
                setTextColor(ContextCompat.getColor(context, R.color.primary_accent));
                setHintTextColor(ContextCompat.getColor(context, R.color.subtext));
            }
            // Lupe-Icon einfärben
            findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)?.apply {
                imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary_accent))
            }
            // Schließen-Button einfärben
            findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)?.apply {
                imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary_accent))
            }
        }
    }

    /**
     * Prüft und fordert die nötigen Berechtigungen über den modernen Permission-Launcher an.
     * Benötigt werden:
     * - READ_MEDIA_AUDIO: Zugriff auf Musikdateien (ab Android 13)
     * - POST_NOTIFICATIONS: Für die Foreground-Service-Benachrichtigung
     */
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Prüfen, ob Audio-Berechtigung bereits erteilt ist
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        // Prüfen, ob Benachrichtigungs-Berechtigung bereits erteilt ist
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            // Fehlende Berechtigungen anfordern
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Alle Berechtigungen vorhanden – Audiodateien sofort laden
            loadAudioFiles()
        }
    }

    /**
     * Registriert den ActivityResultLauncher für den System-Datei-Picker.
     * Nach Auswahl einer Datei wird:
     * 1. Die persistente Leseberechtigung für die URI gesichert
     * 2. Der Pfad in das Eingabefeld geschrieben
     * 3. Metadaten (Titel, Künstler, Genre, Datum) aus der Datei ausgelesen
     */
    private fun setupFilePickerLauncher() {
        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data;
                    if (uri == null) {
                        return@registerForActivityResult;
                    }
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                    currentPathField?.setText(uri.toString());
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(this, uri)

                        val title =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                ?: uri.lastPathSegment ?: "Unbekannt"
                        val artist =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                ?: "Unbekannt"
                        val album =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                                ?: "Unbekannt"
                        val date =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                                ?: LocalDate.now().toString();

                        // Eingabefelder im Add-Dialog mit den ausgelesenen Metadaten befüllen
                        val rootView = currentPathField?.rootView
                        rootView?.findViewById<TextInputEditText>(R.id.etAudioTitle)?.setText(title)
                        rootView?.findViewById<TextInputEditText>(R.id.etAudioArtist)
                            ?.setText(artist)
                        rootView?.findViewById<TextInputEditText>(R.id.etAudioGenre)?.setText(album)
                        rootView?.findViewById<TextInputEditText>(R.id.etAudioDate)?.setText(date)
                        retriever.release()
                    } catch (e: Exception) {
                        Log.e("MainActivity-Metadaten", "Error reading metadata: $e")
                    }
                }
            }
    }

    /**
     * Konfiguriert die Player-Steuerungselemente in der unteren Leiste.
     * Delegiert alle Aktionen an den MusicService.
     */
    private fun setupPlayerControls() {
        binding.btnPause.setOnClickListener { musicService?.togglePlayPause() }
        binding.btnVolume.setOnClickListener { showVolumeControl() }
        binding.btnPrevious.setOnClickListener { musicService?.previous() }
        binding.btnNext.setOnClickListener { musicService?.next() }
    }

    /**
     * Konfiguriert die Sortier-Toggle-Buttons und den Reihenfolge-Button.
     * Die Sortieroptionen sind: Name, Künstler, Genre, Veröffentlichungsdatum.
     * Der Pfeil-Button wechselt zwischen aufsteigend und absteigend.
     */
    private fun setupSortControls() {
        // Reihenfolge-Button: Wechselt zwischen aufsteigend (Pfeil runter) und absteigend (Pfeil hoch)
        binding.btnChangeSortOrder.setOnClickListener {
            order = !order
            binding.btnChangeSortOrder.icon = AppCompatResources.getDrawable(
                this,
                if (order) R.drawable.keyboard_arrow_up_24px else R.drawable.keyboard_arrow_down_24px
            )
            loadAdapter()
        }

        // Toggle-Buttons: Wechseln die Sortieroption
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

    /**
     * Konfiguriert den Floating Action Button (FAB) zum Hinzufügen von Playlists oder Audiodateien.
     * Öffnet ein BottomSheet-Dialog mit zwei Formularen:
     * - "Neue Playlist" (Standard): Name eingeben und speichern
     * - "Neuer Audiotitel": Datei auswählen, Metadaten eingeben und speichern
     */
    private fun setupFabAddDialog() {
        binding.fabAddPlaylist.setOnClickListener {
            val bottomSheet = BottomSheetDialog(this)
            bottomSheet.behavior.isFitToContents = true
            bottomSheet.behavior.skipCollapsed = true
            bottomSheet.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundResource(android.R.color.black)
            val view = layoutInflater.inflate(R.layout.bottomsheetadd, null)

            // UI-Elemente des BottomSheet referenzieren
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

            // Toggle zwischen Playlist- und Audio-Formular
            // Playlist-Formular ist der Default (sichtbar beim Öffnen)
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

            // Datei-Browser öffnen: Startet den System-File-Picker für Audiodateien
            btnBrowse.setOnClickListener {
                currentPathField = etAudioPath;
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "audio/*"  // Nur Audiodateien anzeigen
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                filePickerLauncher.launch(intent)
            }

            // Neue Playlist speichern: Name prüfen, in DB einfügen, Liste aktualisieren
            btnSavePlaylist.setOnClickListener {
                val name = etPlaylistName.text.toString()
                if (name.isNotEmpty()) {
                    val playlist = myPlaylist(
                        playlistID = myDB.getNextAvailablePlaylistID(),
                        playlistTitle = name
                    )
                    myDB.addPlaylistToDatabase(playlist)
                    loadAudioFiles()  // UI aktualisieren
                    bottomSheet.dismiss()
                }
            }

            view.findViewById<TextInputLayout>(R.id.tilAudioDate).setEndIconOnClickListener {
                val calendar = Calendar.getInstance()

                DatePickerDialog(
                    this,
                    { _, year, month, day ->
                        // month ist 0-basiert (Januar = 0), daher +1 für die korrekte Anzeige
                        val formattedDate = String.format("%02d.%02d.%04d", day, month + 1, year)
                        etAudioDate.setText(formattedDate)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            // Neuen Audiotitel speichern: Pflichtfelder prüfen, in DB einfügen und zur "Alle"-Playlist hinzufügen
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
                    myDB.addAudioToPlaylist(audio.audioID, 1)  // Zur "Alle"-Playlist hinzufügen
                    loadAudioFiles()  // UI aktualisieren
                    bottomSheet.dismiss()
                }
            }

            bottomSheet.setContentView(view)
            bottomSheet.show()
        }
    }

    /**
     * Konfiguriert den Einstellungs-Button für die Ordnerauswahl.
     * Öffnet ein BottomSheet mit einer Checkbox-Liste aller Musikordner auf dem Gerät.
     * Der Benutzer kann auswählen, welche Ordner in der App angezeigt werden.
     * Die Auswahl wird in SharedPreferences gespeichert.
     */
    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            val bottomSheet = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.musicfolderlib_holder, null)

            // Alle Ordner mit Musikdateien laden
            val folders = myDB.getAllAudioFolders(this) as MutableList<String>
            val folderAdapter = MyAdapterFolder(folders, this)

            // Gespeicherte Ordnerauswahl laden und im Adapter vorauswählen
            val prefs = applicationContext.getSharedPreferences("AppSettings", MODE_PRIVATE)
            val savedFolders = prefs.getStringSet("selectedFolders", null)

            if (savedFolders != null) {
                folderAdapter.selectedFolders.addAll(savedFolders)
            } else {
                // Beim ersten Öffnen: Alle Ordner vorauswählen
                folderAdapter.selectedFolders.addAll(folders)
            }

            // RecyclerView mit dem Ordner-Adapter konfigurieren
            view.findViewById<RecyclerView>(R.id.rvFolderSelection).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = folderAdapter
            }

            // Bestätigen-Button: Auswahl in SharedPreferences speichern und Songs neu laden
            view.findViewById<MaterialButton>(R.id.btnConfirmFolderChanges).setOnClickListener {
                prefs.edit()
                    .putStringSet("selectedFolders", folderAdapter.selectedFolders)
                    .apply()
                loadAudioFiles()
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
     *
     * Zwei Szenarien:
     * 1. Service hat bereits einen Track geladen: UI direkt aktualisieren
     * 2. Kein Track im Service: Letzten Zustand aus SharedPreferences laden
     *    und den Track im Service vorbereiten (ohne Wiedergabe zu starten)
     */
    private fun restorePlaybackState() {
        musicService?.let { service ->
            if (service.currentTrack != null) {
                // Szenario 1: Track bereits im Service vorhanden
                service.currentTrack?.let { track ->
                    binding.tvTitleText.text = track.audioTitle
                    binding.tvSubTitleText.text = track.audioArtist

                    try {
                        if (service.mediaPlayer.duration > 0) {
                            binding.sbProgress.max = service.mediaPlayer.duration
                            binding.sbProgress.progress = service.mediaPlayer.currentPosition
                        }
                    } catch (_: Exception) {
                        // MediaPlayer kann in bestimmten Zuständen Fehler werfen
                    }

                    // Play/Pause-Icon entsprechend setzen
                    binding.btnPause.setIconResource(
                        if (service.mediaPlayer.isPlaying) R.drawable.pause_24px
                        else R.drawable.play_arrow_24px
                    )
                    seekBarUpdater?.let { handler.post(it) }
                }
            } else {
                // Szenario 2: Letzten Zustand aus SharedPreferences laden
                val prefs =
                    applicationContext.getSharedPreferences("MusicPlayerPrefs", MODE_PRIVATE)
                val trackID = prefs.getInt("currentTrackID", -1)
                val position = prefs.getInt("currentPosition", 0)
                if (trackID == -1 || songList.isEmpty()) return

                // Track in der aktuellen Song-Liste suchen
                val track = songList.find { it.audioID == trackID } ?: return
                val index = songList.indexOf(track)
                if (index == -1) return

                // Track im Service laden (ohne Wiedergabe zu starten)
                service.trackList = songList
                service.loadTrack(track, index)

                try {
                    // Zur gespeicherten Position springen
                    service.mediaPlayer.seekTo(position)
                    binding.sbProgress.progress = position
                    binding.btnPause.setIconResource(R.drawable.play_arrow_24px)
                } catch (_: Exception) {
                    // Fehler beim Seek ignorieren
                }
            }
        }
    }

    /**
     * Filtert die Song-Liste anhand des Suchtextes.
     * Durchsucht Titel, Künstler und Genre (case-insensitive).
     * Bei leerem Suchtext werden alle Songs der aktuellen Playlist angezeigt.
     *
     * @param query Der Suchtext aus der SearchView
     */
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

    /**
     * Startet die Wiedergabe eines Tracks über den MusicService.
     * Übergibt die aktuelle Song-Liste als Wiedergabeliste an den Service.
     *
     * @param track Der abzuspielende Track
     */
    fun playTrack(track: myAudio) {
        musicService?.let {
            it.trackList = songList
            it.playTrack(track, songList.indexOf(track))
        }
    }

    /**
     * Sortiert die Song-Liste entsprechend der aktuellen Sortieroption und Reihenfolge
     * und aktualisiert den RecyclerView-Adapter.
     *
     * Sortieroptionen: Name, Künstler, Genre (alphabetisch), Release (nach Datum).
     * Bei der Datum-Sortierung werden ungültige Datumsformate ans Ende sortiert.
     */
    fun loadAdapter() {
        // Sortierung anwenden
        when (currentSortOption) {
            SortOption.NAME -> if (order) songList.sortByDescending { it.audioTitle } else songList.sortBy { it.audioTitle }
            SortOption.ARTIST -> if (order) songList.sortByDescending { it.audioArtist } else songList.sortBy { it.audioArtist }
            SortOption.GENRE -> if (order) songList.sortByDescending { it.audioGenre } else songList.sortBy { it.audioGenre }
            SortOption.RELEASE -> {
                // Datum-Sortierung: String "dd.MM.yyyy" in Date parsen
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                val parseDate = { track: myAudio ->
                    try {
                        sdf.parse(track.audioRelDate)
                    } catch (_: Exception) {
                        null  // Ungültige Daten werden als null behandelt (ans Ende sortiert)
                    }
                }
                if (order) songList.sortByDescending(parseDate) else songList.sortBy(parseDate)
            }
        }

        // RecyclerView mit neuem Adapter bestücken
        binding.rvAudioTracks.layoutManager = LinearLayoutManager(this)
        val adapter = MyAdapterAudio(
            songList, this, currentPlaylistID,
            onTrackClicked = { track -> playTrack(track) },
            onTrackEdited = { track ->
                myDB.editAudioEntry(track)
                loadAudioFiles()
            },
            onAddToPlaylist = { track -> showPlaylistSelector(track) },
            onRemoveFromPlaylist = { track ->
                myDB.removeAudioFromPlaylist(track.audioID, currentPlaylistID)
                loadAudioFiles()
            }
        )
        binding.rvAudioTracks.adapter = adapter
    }

    /**
     * Zeigt das BottomSheet zur Playlist-Auswahl für einen Song an.
     * Zeigt alle benutzerdefinierten Playlists (ohne "Alle") mit Checkboxen.
     * Bereits zugewiesene Playlists sind vorausgewählt.
     *
     * Bei Bestätigung werden neue Zuweisungen erstellt und entfernte gelöscht.
     *
     * @param track Der Song, der zu Playlists hinzugefügt werden soll
     */
    private fun showPlaylistSelector(track: myAudio) {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.playlist_selectorholder, null)

        // Alle benutzerdefinierten Playlists laden (ohne die "Alle"-Playlist)
        val playlists = myDB.getAllPlaylistsFromDB()
            .filter { it.playlistID != 1 }
            .toMutableList()

        val selectionAdapter = MyAdapterPlaylistSelect(playlists)

        // Bereits zugewiesene Playlists vorauswählen
        val existingPlaylists = myDB.getPlaylistIDsForAudio(track.audioID)
        selectionAdapter.selectedPlaylists.addAll(existingPlaylists)

        // RecyclerView mit dem Auswahl-Adapter konfigurieren
        view.findViewById<RecyclerView>(R.id.rvPlaylistSelection).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = selectionAdapter
        }

        // Bestätigen-Button: Änderungen (neue/entfernte Zuweisungen) verarbeiten
        view.findViewById<MaterialButton>(R.id.btnConfirmAddToPlaylist).setOnClickListener {
            // Neue Zuweisungen erstellen (in Auswahl, aber noch nicht in DB)
            for (playlistID in selectionAdapter.selectedPlaylists) {
                if (!existingPlaylists.contains(playlistID)) {
                    myDB.addAudioToPlaylist(track.audioID, playlistID)
                }
            }
            // Entfernte Zuweisungen löschen (in DB, aber nicht mehr in Auswahl)
            for (playlistID in existingPlaylists) {
                if (!selectionAdapter.selectedPlaylists.contains(playlistID)) {
                    myDB.removeAudioFromPlaylist(track.audioID, playlistID)
                }
            }
            loadAudioFiles()
            bottomSheet.dismiss()
        }

        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    /**
     * Hauptmethode zum Laden und Synchronisieren aller Audiodateien und Playlists.
     *
     * Ablauf:
     * 1. Alle Musikdateien vom MediaStore lesen
     * 2. Gelöschte Songs aus der Datenbank entfernen
     * 3. Neue Songs in die Datenbank und die "Alle"-Playlist einfügen
     * 4. Songs der aktuellen Playlist laden
     * 5. Playlist-RecyclerView aktualisieren
     * 6. Song-RecyclerView aktualisieren (über loadAdapter)
     */
    fun loadAudioFiles() {
        // Schritt 1: Alle Musikdateien aus dem MediaStore lesen
        val mediaList = myDB.getAllMp3Files(this) as MutableList<myAudio>

        // Schritt 2: Songs entfernen, die nicht mehr auf dem Gerät vorhanden sind
        myDB.removeDeletedAudios(mediaList.map { it.audioID })

        // Schritt 3: Neue Songs in die Datenbank einfügen und der "Alle"-Playlist zuweisen
        for (audio in mediaList) {
            if (!myDB.audioExists(audio.audioID)) {
                myDB.addAudioToDatabase(audio)
                myDB.addAudioToPlaylist(audio.audioID, 1)  // Zur "Alle"-Playlist
            }
        }

        // Schritt 4: Songs der aktuell gewählten Playlist laden
        songList = myDB.getAudiosByPlaylist(currentPlaylistID) as MutableList<myAudio>

        // Schritt 5: Playlist-RecyclerView aktualisieren
        val playLibList = myDB.getAllPlaylistsFromDB() as MutableList<myPlaylist>
        binding.rvPlaylists.layoutManager = LinearLayoutManager(this)
        val playListAdapter = MyAdapterPlaylist(
            playLibList, this,
            onPlaylistClicked = { playlist ->
                // Playlist auswählen: Songs dieser Playlist laden
                currentPlaylistID = playlist.playlistID
                songList = myDB.getAudiosByPlaylist(playlist.playlistID) as MutableList<myAudio>
                loadAdapter()
            },
            onPlaylistEdited = { playlist, newName ->
                // Playlist umbenennen
                playlist.playlistTitle = newName
                myDB.editPlaylistEntry(playlist)
                loadAudioFiles()
            },
            onPlaylistDeleted = { playlist ->
                // Playlist löschen
                myDB.deletePlaylistEntry(playlist)
                loadAudioFiles()
            }
        )
        binding.rvPlaylists.adapter = playListAdapter

        // Schritt 6: Song-RecyclerView aktualisieren (sortiert und filtert)
        loadAdapter()
    }

    //endregion
}

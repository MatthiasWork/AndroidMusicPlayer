package com.example.androidprojektaudioplayer

import android.Manifest
import android.app.DatePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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

/**
 * Hauptactivity der Audioplayer-App.
 * Zeigt die Song-Liste und Playlists an, bietet Steuerungselemente für die Wiedergabe
 * (Play/Pause, Vor, Zurück), eine Suchfunktion, Sortiermöglichkeiten und
 * Dialoge zum Hinzufügen von Songs/Playlists sowie zur Ordnerauswahl.
 * Kommuniziert mit dem MusicService für die Wiedergabe.
 */
class MainActivity : AppCompatActivity() {

    /** View-Binding für den Zugriff auf alle UI-Elemente des Hauptlayouts. */
    private lateinit var binding: ActivityMainBinding;

    /** Datenbankhelfer für alle CRUD-Operationen auf Audio- und Playlist-Tabellen. */
    private lateinit var myDB: DataBaseHelper;

    /** Referenz auf den MusicService für die Steuerung der Musikwiedergabe. */
    private var musicService: MusicService? = null;

    /** Gibt an, ob die Activity aktuell an den MusicService gebunden ist. */
    private var serviceBound = false;

    /** Gibt die aktuelle Sortierreihenfolge an: false = aufsteigend, true = absteigend. */
    private var order: Boolean = false;

    /** Handler für zeitgesteuerte UI-Updates (SeekBar-Aktualisierung). */
    private val handler = Handler(Looper.getMainLooper());

    /** Die aktuell angezeigte Song-Liste (kann gefiltert und sortiert sein). */
    private var songList: MutableList<myAudio> = mutableListOf();

    /** ID der aktuell ausgewählten Playlist (1 = Standard-Playlist "Alle"). */
    private var currentPlaylistID: Int = 1;

    /** Launcher für den Datei-Picker, um Audiodateien vom Gerät auszuwählen. */
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>;

    /** Referenz auf das Pfad-Eingabefeld im Add-Dialog, damit der File-Picker es befüllen kann. */
    private var currentPathField: TextInputEditText? = null;

    /**
     * Enum für die verfügbaren Sortieroptionen der Song-Liste.
     * NAME = nach Titel, ARTIST = nach Künstler, GENRE = nach Genre/Album,
     * RELEASE = nach Veröffentlichungsdatum.
     */
    enum class SortOption { NAME, ARTIST, GENRE, RELEASE };

    /** Die aktuell ausgewählte Sortieroption (Standard: nach Name). */
    private var currentSortOption: SortOption = SortOption.NAME;

    /**
     * ServiceConnection für die Verbindung zum MusicService.
     * Registriert Callbacks für Track- und Statusänderungen und
     * stellt den Wiedergabestatus nach der Verbindung wieder her.
     */
    private val serviceConnection = object : ServiceConnection {
        /**
         * Wird aufgerufen, wenn die Verbindung zum MusicService hergestellt wurde.
         * Registriert Callbacks für UI-Updates und stellt den letzten Wiedergabestatus wieder her.
         *
         * @param name    Der Komponentenname des verbundenen Service
         * @param service Der IBinder für die Kommunikation mit dem Service
         */
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true

            // Callback: Bei Trackwechsel die Titelleiste und SeekBar aktualisieren
            musicService?.onTrackChanged = { track ->
                binding.tvTitleText.text = track.audioTitle
                binding.tvSubTitleText.text = track.audioArtist
                binding.sbProgress.max = musicService?.mediaPlayer?.duration ?: 0
                binding.sbProgress.progress = 0
                handler.removeCallbacks(updateSeekBar)
                handler.post(updateSeekBar)
            }

            // Callback: Bei Play/Pause-Wechsel das Icon aktualisieren
            musicService?.onPlayStateChanged = { isPlaying ->
                binding.btnPause.setIconResource(
                    if (isPlaying) R.drawable.pause_24px else R.drawable.play_arrow_24px
                )
            }

            // Letzten Wiedergabestatus aus SharedPreferences wiederherstellen
            restorePlaybackState()
        }

        /**
         * Wird aufgerufen, wenn die Verbindung zum Service unerwartet getrennt wird.
         *
         * @param name Der Komponentenname des getrennten Service
         */
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }

    /**
     * Runnable für die regelmäßige Aktualisierung der SeekBar.
     * Liest jede Sekunde die aktuelle Position aus dem MediaPlayer
     * und setzt den Fortschritt der SeekBar entsprechend.
     */
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

    /**
     * Wird beim Erstellen der Activity aufgerufen.
     * Initialisiert das Layout, die Datenbank, den MusicService und alle UI-Listener.
     * Prüft die Berechtigung zum Lesen von Audiodateien und lädt bei Vorhandensein
     * die Musikdateien. Richtet den File-Picker, die Suchleiste, Sortieroptionen,
     * den FAB-Dialog zum Hinzufügen und den Einstellungs-Dialog ein.
     *
     * @param savedInstanceState Gespeicherter Zustand der Activity (falls vorhanden)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        // Edge-to-Edge: Padding für Systemleisten setzen
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Erzwingt den Light-Mode (kein Dark-Theme)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        myDB = DataBaseHelper(this)

        android.util.Log.d("MainActivity", "Database cleared!")

        // MusicService starten (falls noch nicht läuft) und an die Activity binden
        Intent(this, MusicService::class.java).also { intent ->
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        // Listener: Fokus von der Suchleiste entfernen, wenn der Benutzer auf den Hintergrund tippt
        binding.main.setOnTouchListener { _, _ ->
            binding.svSearch.clearFocus()
            false
        }

        // Listener: Suchleiste - filtert die Song-Liste bei jeder Texteingabe
        binding.svSearch.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            /**
             * Wird aufgerufen, wenn der Benutzer die Suche bestätigt (Enter drückt).
             * Nicht verwendet, da die Filterung bereits in onQueryTextChange erfolgt.
             */
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            /**
             * Wird bei jeder Änderung des Suchtextes aufgerufen.
             * Filtert die Song-Liste nach Titel, Künstler und Genre.
             *
             * @param newText Der aktuelle Suchtext
             * @return true, da das Event verarbeitet wurde
             */
            override fun onQueryTextChange(newText: String?): Boolean {
                filterSongs(newText ?: "")
                return true
            }
        })

        // Suchleiste: Farben der Icons und des Textes anpassen
        binding.svSearch.apply {
            // Text-Farbe und Hint-Farbe setzen
            findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.apply {
                setTextColor(ContextCompat.getColor(context, R.color.primary_accent))
                setHintTextColor(ContextCompat.getColor(context, R.color.subtext))
            }

            // Lupe-Icon einfärben
            findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)?.apply {
                setColorFilter(ContextCompat.getColor(context, R.color.primary_accent))
                imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary_accent))
            }

            // Schließen-Icon einfärben
            findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)?.apply {
                setColorFilter(ContextCompat.getColor(context, R.color.primary_accent))
                imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary_accent))
            }
        }

        // Listener: SeekBar - erlaubt dem Benutzer, im Song vor- und zurückzuspulen
        binding.sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            /**
             * Wird aufgerufen, wenn sich der SeekBar-Fortschritt ändert.
             * Setzt die Wiedergabeposition nur bei manueller Benutzerinteraktion.
             */
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.mediaPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Berechtigung zum Lesen von Audiodateien prüfen und ggf. anfordern
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

        // File-Picker registrieren: Wird genutzt, um manuell Audiodateien hinzuzufügen.
        // Nach der Auswahl werden die Metadaten (Titel, Künstler, Genre, Datum) automatisch
        // aus der Datei ausgelesen und in die Eingabefelder des Add-Dialogs eingetragen.
        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data
                    if (uri != null) {
                        // Dauerhafte Leseberechtigung für die ausgewählte Datei sichern
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        currentPathField?.setText(uri.toString())

                        // Metadaten aus der Audiodatei auslesen
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
                                    ?: ""

                            // Ausgelesene Metadaten in die Eingabefelder des Dialogs eintragen
                            val rootView = currentPathField?.rootView
                            rootView?.findViewById<TextInputEditText>(R.id.etAudioTitle)
                                ?.setText(title)
                            rootView?.findViewById<TextInputEditText>(R.id.etAudioArtist)
                                ?.setText(artist)
                            rootView?.findViewById<TextInputEditText>(R.id.etAudioGenre)
                                ?.setText(album)
                            rootView?.findViewById<TextInputEditText>(R.id.etAudioDate)
                                ?.setText(date)
                            retriever.release()
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "MainActivity-Metadaten",
                                "Error reading metadata: $e"
                            )
                        }
                    }
                }
            }

        // Listener: Play/Pause-Button - wechselt zwischen Wiedergabe und Pause
        binding.btnPause.setOnClickListener {
            musicService?.togglePlayPause()
        }

        // Listener: Lautstärke-Button - zeigt den System-Lautstärkeregler an
        binding.btnVolume.setOnClickListener {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI
            )
        }

        // Listener: Sortierreihenfolge umkehren (aufsteigend <-> absteigend)
        // Wechselt das Icon zwischen Pfeil-hoch und Pfeil-runter
        binding.btnChangeSortOrder.setOnClickListener {
            order = !order
            if (order) {
                binding.btnChangeSortOrder.icon = getDrawable(R.drawable.keyboard_arrow_up_24px)
            } else {
                binding.btnChangeSortOrder.icon = getDrawable(R.drawable.keyboard_arrow_down_24px)
            }
            loadAdapter()
        }

        // Listener: Vorheriger Song
        binding.btnPrevious.setOnClickListener {
            musicService?.previous()
        }

        // Listener: Nächster Song
        binding.btnNext.setOnClickListener {
            musicService?.next()
        }

        // Listener: Sortier-Toggle-Buttons (Name, Künstler, Genre, Veröffentlichung)
        // Ändert die Sortieroption und lädt die Liste neu
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

        // Listener: FAB (Floating Action Button) - öffnet den Dialog zum Hinzufügen
        // von Playlists oder Audiodateien über ein BottomSheet
        binding.fabAddPlaylist.setOnClickListener {
            val bottomSheet = BottomSheetDialog(this)
            bottomSheet.behavior.isFitToContents = true
            bottomSheet.behavior.skipCollapsed = true
            bottomSheet.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent);
            val view = layoutInflater.inflate(R.layout.bottomsheetadd, null)

            // UI-Elemente des BottomSheet-Dialogs finden
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

            // Toggle zwischen "Playlist hinzufügen" und "Audio hinzufügen":
            // Zeigt das jeweils passende Formular an und blendet das andere aus
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

            // Listener: Datei-Browser öffnen, um eine Audiodatei auszuwählen
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

            // Listener: Neue Playlist speichern
            // Erstellt einen neuen Playlist-Eintrag in der Datenbank und aktualisiert die Anzeige
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

            // Listener: Datumsauswahl über einen DatePickerDialog für das Veröffentlichungsdatum
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

            // Listener: Neuen Audiotitel speichern
            // Erstellt einen neuen Audio-Eintrag in der Datenbank, verknüpft ihn mit der
            // Standard-Playlist "Alle" (ID 1) und aktualisiert die Anzeige
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
                    myDB.addAudioToPlaylist(audio.audioID, 1)  // Zur "Alle"-Playlist hinzufügen
                    ladeAudioDateien()
                    bottomSheet.dismiss()
                }
            }

            bottomSheet.setContentView(view)
            bottomSheet.show()
        }

        // Listener: Karte unten anklicken, um zur Detailansicht des aktuellen Songs zu wechseln
        binding.cardOpenDetail.setOnClickListener {
            val intent = Intent(this, DetailActivity::class.java)
            startActivity(intent)
        }

        // Listener: Einstellungs-Button - öffnet die Ordnerauswahl als BottomSheet
        // Der Benutzer kann auswählen, welche Ordner nach Musikdateien durchsucht werden sollen
        binding.btnSettings.setOnClickListener {
            val bottomSheet = BottomSheetDialog(this)
            val view = layoutInflater.inflate(
                R.layout.musicfolderlib_holder,
                null
            )

            // Alle verfügbaren Musikordner vom Gerät laden
            val folders = myDB.getAllAudioFolders(this) as MutableList<String>
            val folderAdapter = MyAdapterFolder(folders, this)

            // Aktuell ausgewählte Ordner aus SharedPreferences laden und vorauswählen
            val prefs = applicationContext.getSharedPreferences("AppSettings", MODE_PRIVATE)
            val savedFolders = prefs.getStringSet("selectedFolders", null)

            if (savedFolders != null) {
                folderAdapter.selectedFolders.addAll(savedFolders)
            } else {
                // Wenn nichts gespeichert ist, alle Ordner standardmäßig vorauswählen
                folderAdapter.selectedFolders.addAll(folders)
            }

            // RecyclerView mit dem Ordner-Adapter konfigurieren
            view.findViewById<RecyclerView>(R.id.rvFolderSelection).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = folderAdapter
            }

            // Listener: Ordnerauswahl bestätigen und Songs neu laden
            view.findViewById<MaterialButton>(R.id.btnConfirmFolderChanges).setOnClickListener {
                // Ausgewählte Ordner in SharedPreferences speichern
                prefs.edit()
                    .putStringSet("selectedFolders", folderAdapter.selectedFolders)
                    .apply()

                // Songs mit dem neuen Ordner-Filter neu laden
                ladeAudioDateien()
                bottomSheet.dismiss()
            }

            bottomSheet.setContentView(view)
            bottomSheet.show()
        }
    }

    /**
     * Verarbeitet das Ergebnis der Berechtigungsanfrage für den Zugriff auf Audiodateien.
     * Wenn die Berechtigung gewährt wurde, werden die Audiodateien geladen.
     *
     * @param requestCode  Der Request-Code der Berechtigungsanfrage (1 = Audio-Berechtigung)
     * @param permissions  Die angefragten Berechtigungen
     * @param grantResults Die Ergebnisse (gewährt oder verweigert)
     */
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

    /**
     * Wird aufgerufen, wenn die Activity wieder in den Vordergrund kommt.
     * Lädt die Audiodateien neu und aktualisiert die UI mit dem aktuellen
     * Wiedergabestatus des MusicService.
     */
    override fun onResume() {
        super.onResume()
        ladeAudioDateien()
        if (serviceBound) {
            restorePlaybackState()
        }
    }

    /**
     * Wird aufgerufen, wenn die Activity zerstört wird.
     * Stoppt die SeekBar-Updates und löst die Bindung zum MusicService.
     */
    override fun onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBar);
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    /**
     * Stellt den Wiedergabestatus aus dem MusicService oder den SharedPreferences wieder her.
     * Wenn der Service bereits einen Track geladen hat, wird die UI damit aktualisiert.
     * Andernfalls wird versucht, den zuletzt gespielten Track aus den gespeicherten
     * Preferences zu laden und an der letzten Position fortzusetzen (ohne automatisch abzuspielen).
     */
    private fun restorePlaybackState() {
        musicService?.let { service ->
            if (service.currentTrack != null) {
                // Service hat bereits einen Track geladen – UI damit aktualisieren
                service.currentTrack?.let { track ->
                    binding.tvTitleText.text = track.audioTitle
                    binding.tvSubTitleText.text = track.audioArtist

                    try {
                        if (service.mediaPlayer.duration > 0) {
                            binding.sbProgress.max = service.mediaPlayer.duration
                            binding.sbProgress.progress = service.mediaPlayer.currentPosition
                        }
                    } catch (e: Exception) {
                        // MediaPlayer evtl. noch nicht bereit – ignorieren
                    }

                    // Play/Pause-Icon basierend auf dem aktuellen Status setzen
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
                // Kein Track im Service – versuchen, den letzten Zustand aus SharedPreferences zu laden
                val prefs =
                    applicationContext.getSharedPreferences("MusicPlayerPrefs", MODE_PRIVATE)
                val trackID = prefs.getInt("currentTrackID", -1)
                val position = prefs.getInt("currentPosition", 0)
                android.util.Log.d(
                    "MainActivity",
                    "LOADED: TrackID=$trackID, Position=$position, SongListSize=${songList.size}"
                )
                val wasPlaying = prefs.getBoolean("wasPlaying", false)
                if (trackID == -1 || songList.isEmpty()) return

                // Track in der aktuellen Song-Liste anhand der ID suchen
                val track = songList.find { it.audioID == trackID } ?: return
                val index = songList.indexOf(track)
                if (index == -1) return

                // Track im Service laden (ohne Wiedergabe zu starten)
                service.trackList = songList
                service.loadTrack(track, index);

                // Zur gespeicherten Position spulen
                handler.postDelayed({
                    try {
                        service.mediaPlayer.seekTo(position)
                        binding.sbProgress.progress = position
                        binding.btnPause.setIconResource(R.drawable.play_arrow_24px)
                    } catch (e: Exception) {
                        // Fehler beim Seek ignorieren
                    }
                }, 0)
            }
        }
    }

    /**
     * Filtert die Song-Liste anhand des Suchtextes.
     * Durchsucht Titel, Künstler und Genre (case-insensitive).
     * Bei leerem Suchtext werden alle Songs der aktuellen Playlist angezeigt.
     *
     * @param query Der Suchtext aus der Suchleiste
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
     * Startet die Wiedergabe eines bestimmten Tracks über den MusicService.
     * Übergibt dabei die aktuelle Song-Liste an den Service, damit Vor/Zurück funktioniert.
     *
     * @param track Der abzuspielende Audiotitel
     */
    fun playTrack(track: myAudio) {
        musicService?.let {
            it.trackList = songList;
            val index = songList.indexOf(track);
            it.playTrack(track, index);
        }
    }

    /**
     * Sortiert die Song-Liste nach der aktuell ausgewählten Sortieroption und Reihenfolge
     * und aktualisiert den RecyclerView-Adapter mit der sortierten Liste.
     * Konfiguriert den Audio-Adapter mit Callbacks für Track-Klick, Bearbeiten,
     * Playlist-Zuordnung und Entfernen aus Playlist.
     */
    fun loadAdapter() {
        // Song-Liste je nach gewählter Sortierung und Reihenfolge sortieren
        when (currentSortOption) {
            SortOption.NAME -> if (order) songList.sortByDescending { it.audioTitle } else songList.sortBy { it.audioTitle }
            SortOption.ARTIST -> if (order) songList.sortByDescending { it.audioArtist } else songList.sortBy { it.audioArtist }
            SortOption.GENRE -> if (order) songList.sortByDescending { it.audioGenre } else songList.sortBy { it.audioGenre }
            SortOption.RELEASE -> {
                // Datum-Strings parsen und danach sortieren
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

        // RecyclerView mit dem Audio-Adapter konfigurieren
        binding.rvAudioTracks.layoutManager = LinearLayoutManager(this)
        val adapter = MyAdapterAudio(
            songList, this, currentPlaylistID,
            // Callback: Song abspielen, wenn der Benutzer darauf tippt
            onTrackClicked = { track -> playTrack(track) },
            // Callback: Song-Daten bearbeitet – Änderungen in der Datenbank speichern
            onTrackEdited = { track ->
                myDB.editAudioEntry(track)
                ladeAudioDateien()
            },
            // Callback: Song zu Playlist hinzufügen – BottomSheet mit Playlist-Auswahl anzeigen
            onAddToPlaylist = { track ->
                val bottomSheet = BottomSheetDialog(this)
                val view = layoutInflater.inflate(R.layout.playlist_selectorholder, null)

                // Alle Playlists außer "Alle" (ID 1) laden
                val playlists = myDB.getAllPlaylistsFromDB()
                    .filter { it.playlistID != 1 }
                    .toMutableList()

                val selectionAdapter = MyAdapterPlaylistSelect(playlists)
                // Bereits zugeordnete Playlists vorauswählen
                val existingPlaylists = myDB.getPlaylistIDsForAudio(track.audioID)
                selectionAdapter.selectedPlaylists.addAll(existingPlaylists)

                view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvPlaylistSelection)
                    .apply {
                        layoutManager = LinearLayoutManager(this@MainActivity)
                        adapter = selectionAdapter
                    }

                // Listener: Playlist-Zuordnungen bestätigen
                // Fügt neue Zuordnungen hinzu und entfernt abgewählte Zuordnungen
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
            // Callback: Song aus der aktuellen Playlist entfernen
            onRemoveFromPlaylist = { track ->
                myDB.removeAudioFromPlaylist(track.audioID, currentPlaylistID)
                ladeAudioDateien()
            }
        )
        binding.rvAudioTracks.adapter = adapter
    }

    /**
     * Hauptmethode zum Laden und Synchronisieren aller Audiodateien und Playlists.
     *
     * Ablauf:
     * 1. Alle Musikdateien vom MediaStore laden (mit Ordner-Filter)
     * 2. Gelöschte Songs aus der Datenbank entfernen
     * 3. Neue Songs zur Datenbank hinzufügen und mit der "Alle"-Playlist verknüpfen
     * 4. Songs der aktuellen Playlist laden
     * 5. Playlist-RecyclerView mit allen Playlists aktualisieren
     * 6. Audio-RecyclerView über loadAdapter() aktualisieren
     */
    fun ladeAudioDateien() {
        // Alle Musikdateien aus dem MediaStore laden
        val mediaList = myDB.getAllMp3Files(this) as MutableList<myAudio>
        // Nicht mehr vorhandene Songs aus der Datenbank entfernen
        myDB.removeDeletedAudios(mediaList.map { it.audioID })
        // Neue Songs in die Datenbank einfügen und mit "Alle"-Playlist verknüpfen
        for (audio in mediaList) {
            if (!myDB.audioExists(audio.audioID)) {
                myDB.addAudioToDatabase(audio)
                myDB.addAudioToPlaylist(audio.audioID, 1)
            }
        }

        // Songs der aktuell ausgewählten Playlist laden
        songList = myDB.getAudiosByPlaylist(currentPlaylistID) as MutableList<myAudio>

        // Playlist-RecyclerView aktualisieren
        val playLibList: MutableList<myPlaylist> =
            myDB.getAllPlaylistsFromDB() as MutableList<myPlaylist>
        binding.rvPlaylists.layoutManager = LinearLayoutManager(this)
        val playListAdapter = MyAdapterPlaylist(
            playLibList, this,
            // Callback: Playlist angeklickt – Songs dieser Playlist laden und anzeigen
            onPlaylistClicked = { playlist ->
                currentPlaylistID = playlist.playlistID
                songList = myDB.getAudiosByPlaylist(playlist.playlistID) as MutableList<myAudio>
                loadAdapter()
            },
            // Callback: Playlist umbenannt – Änderung in der Datenbank speichern
            onPlaylistEdited = { playlist, newName ->
                playlist.playlistTitle = newName
                myDB.editPlaylistEntry(playlist)
                ladeAudioDateien()
            },
            // Callback: Playlist gelöscht – aus der Datenbank entfernen
            onPlaylistDeleted = { playlist ->
                myDB.deletePlaylistEntry(playlist)
                ladeAudioDateien()
            }
        )
        binding.rvPlaylists.adapter = playListAdapter
        loadAdapter();
    }
}

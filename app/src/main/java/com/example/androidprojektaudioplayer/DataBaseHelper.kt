package com.example.androidprojektaudioplayer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/** Log-Tag für alle Datenbankoperationen. */
private const val TAG = "SoundioMusikPlayer"

/** Versionsnummer der Datenbank – bei Schemaänderungen erhöhen. */
private const val DATABASE_VERSION = 1

/** Name der SQLite-Datenbankdatei auf dem Gerät. */
private const val DATABASE_NAME = "Musicman"

// ===== Tabelle: Audio =====
/** Tabellenname für Audiotitel. */
private const val TABLE_AUDIO = "Audiotitel"
/** Spalte: Primärschlüssel (Audio-ID, entspricht der MediaStore-ID). */
private const val AUDIO_ID = "AudioID"
/** Spalte: Titel des Songs. */
private const val AUDIO_TITLE = "AudioTitle"
/** Spalte: Künstler/Interpret. */
private const val AUDIO_ARTIST = "AudioArtist"
/** Spalte: Genre des Songs. */
private const val AUDIO_GENRE = "AudioGenre"
/** Spalte: Veröffentlichungsdatum (Format "dd.MM.yyyy"). */
private const val AUDIO_RELDATE = "AudioRelDate"
/** Spalte: URI-Pfad zur Audiodatei. */
private const val AUDIO_SAVEPATH = "AudioPath"

// ===== Tabelle: Playlist =====
/** Tabellenname für Playlists. */
private const val TABLE_PLAYLIST = "Playlist"
/** Spalte: Primärschlüssel (Playlist-ID). */
private const val PLAYLIST_ID = "PlaylistID"
/** Spalte: Anzeigename der Playlist. */
private const val PLAYLIST_TITLE = "PlaylistTitle"

// ===== Zwischentabelle: Audio <-> Playlist (Many-to-Many) =====
/** Tabellenname für die Verknüpfung zwischen Audio und Playlist. */
private const val TABLE_PLAYAUDIO = "AudioTrackPlaylist"
/** Spalte: Fremdschlüssel auf die Audio-Tabelle. */
private const val FKPK_AUDIOPLAYLIST = "AudioPlaylist"
/** Spalte: Fremdschlüssel auf die Playlist-Tabelle. */
private const val FKPK_PLAYLISTAUDIO = "PlaylistAudio"

/**
 * SQLiteOpenHelper-Klasse, die die gesamte Datenbank-Logik der App kapselt.
 *
 * Verwaltet drei Tabellen:
 * - **Audiotitel**: Speichert alle Songs mit Metadaten
 * - **Playlist**: Speichert benutzerdefinierte Playlists
 * - **AudioTrackPlaylist**: Zwischentabelle für die Many-to-Many-Beziehung
 *
 * Bietet CRUD-Operationen für Audio und Playlists sowie Zugriff auf den
 * Android MediaStore zum Einlesen der auf dem Gerät vorhandenen Musikdateien.
 */
class DataBaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    /**
     * Wird beim erstmaligen Erstellen der Datenbank aufgerufen.
     * Legt die drei Tabellen an und fügt die Standard-Playlist "Alle" ein.
     *
     * Die Zwischentabelle nutzt einen zusammengesetzten Primärschlüssel
     * und ON DELETE CASCADE, damit beim Löschen eines Songs oder einer Playlist
     * die zugehörigen Verknüpfungen automatisch entfernt werden.
     */
    override fun onCreate(db: SQLiteDatabase?) {
        db?.let {
            // Audio-Tabelle erstellen
            it.execSQL(
                "CREATE TABLE $TABLE_AUDIO(" +
                        "$AUDIO_ID INTEGER PRIMARY KEY, " +
                        "$AUDIO_TITLE TEXT, " +
                        "$AUDIO_ARTIST TEXT, " +
                        "$AUDIO_GENRE TEXT, " +
                        "$AUDIO_RELDATE TEXT, " +
                        "$AUDIO_SAVEPATH TEXT)"
            )

            // Playlist-Tabelle erstellen
            it.execSQL(
                "CREATE TABLE $TABLE_PLAYLIST(" +
                        "$PLAYLIST_ID INTEGER PRIMARY KEY, " +
                        "$PLAYLIST_TITLE TEXT)"
            )

            // Zwischentabelle mit zusammengesetztem PK und CASCADE-Löschregeln erstellen
            it.execSQL(
                "CREATE TABLE $TABLE_PLAYAUDIO(" +
                        "$FKPK_AUDIOPLAYLIST INTEGER, " +
                        "$FKPK_PLAYLISTAUDIO INTEGER, " +
                        "PRIMARY KEY($FKPK_AUDIOPLAYLIST, $FKPK_PLAYLISTAUDIO), " +
                        "FOREIGN KEY($FKPK_AUDIOPLAYLIST) REFERENCES $TABLE_AUDIO($AUDIO_ID) ON DELETE CASCADE, " +
                        "FOREIGN KEY($FKPK_PLAYLISTAUDIO) REFERENCES $TABLE_PLAYLIST($PLAYLIST_ID) ON DELETE CASCADE)"
            )

            // Standard-Playlist "Alle" anlegen – enthält immer alle Songs
            val container = ContentValues()
            container.put(PLAYLIST_TITLE, "Alle")
            it.insert(TABLE_PLAYLIST, null, container)
        } ?: Log.e(TAG, "Fehler: Datenbankinstanz ist null bei onCreate")
    }

    /**
     * Wird bei einem Versionsupgrade aufgerufen. Aktuell nicht implementiert,
     * da die Datenbank noch in Version 1 ist.
     */
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    /**
     * Wird bei jedem Öffnen der Datenbank aufgerufen.
     * Aktiviert die Foreign-Key-Constraints, da SQLite diese standardmäßig deaktiviert hat.
     * Ohne diese Aktivierung würden die ON DELETE CASCADE-Regeln nicht greifen.
     */
    override fun onOpen(db: SQLiteDatabase?) {
        super.onOpen(db)
        db?.setForeignKeyConstraintsEnabled(true)
    }

    /**
     * Liest alle Ordner aus, in denen Musikdateien auf dem Gerät gespeichert sind.
     * Nutzt RELATIVE_PATH (modern, ab API 29) statt des deprecated DATA-Feldes.
     *
     * @param context Der Context für den ContentResolver-Zugriff
     * @return Sortierte Liste aller Ordnerpfade, die Musikdateien enthalten
     */
    fun getAllAudioFolders(context: Context): List<String> {
        val folders = mutableSetOf<String>()

        // Nur den relativen Pfad abfragen – reicht für die Ordnerauswahl
        val projection = arrayOf(MediaStore.Audio.Media.RELATIVE_PATH)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, null
        )?.use { cursor ->
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val relativePath = cursor.getString(pathColumn)
                if (!relativePath.isNullOrEmpty()) {
                    // Trailing "/" entfernen für konsistente Vergleiche mit SharedPreferences
                    folders.add(relativePath.trimEnd('/'))
                }
            }
        }
        return folders.sorted()
    }

    //region Gemeinsame Hilfsmethoden

    /**
     * Ermittelt die nächste verfügbare ID für eine Tabelle.
     * Berechnet MAX(column) + 1 aus der angegebenen Tabelle.
     * Deduplizierte Methode – ersetzt separate getNextAvailableAudioID und getNextAvailablePlaylistID.
     *
     * @param table  Name der Tabelle (z.B. TABLE_AUDIO)
     * @param column Name der ID-Spalte (z.B. AUDIO_ID)
     * @return Die nächste freie ID (1, falls die Tabelle leer ist)
     */
    private fun getNextAvailableID(table: String, column: String): Int {
        readableDatabase.rawQuery("SELECT MAX($column) FROM $table", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) + 1 else 1
        }
    }

    /** Gibt die nächste verfügbare Audio-ID zurück. */
    fun getNextAvailableAudioID(): Int = getNextAvailableID(TABLE_AUDIO, AUDIO_ID)

    /** Gibt die nächste verfügbare Playlist-ID zurück. */
    fun getNextAvailablePlaylistID(): Int = getNextAvailableID(TABLE_PLAYLIST, PLAYLIST_ID)

    //endregion

    //region CRUD für Audio

    /**
     * Liest alle Musikdateien aus dem Android MediaStore und gibt sie als Liste zurück.
     * Berücksichtigt den Ordner-Filter aus den SharedPreferences ("selectedFolders").
     *
     * Nutzt RELATIVE_PATH statt des deprecated DATA-Feldes für die Pfadermittlung.
     * Das Hinzufügedatum (DATE_ADDED) wird als Unix-Timestamp geliefert und
     * in das Format "dd.MM.yyyy" konvertiert.
     *
     * @param context Der Context für ContentResolver und SharedPreferences
     * @return Liste aller gefundenen Audiodateien als myAudio-Objekte
     */
    fun getAllMp3Files(context: Context): List<myAudio> {
        val mp3List = mutableListOf<myAudio>()

        // Gespeicherte Ordnerauswahl aus den Einstellungen laden
        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val selectedFolders = prefs.getStringSet("selectedFolders", null)

        // Projektion: Welche Spalten aus dem MediaStore abgefragt werden
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        // Nur als Musik markierte Dateien berücksichtigen
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"  // Alphabetisch sortiert
        )?.use { cursor ->
            // Spaltenindizes einmalig ermitteln (performanter als pro Zeile)
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
            val relDateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getInt(idColumn)
                val name = cursor.getString(nameColumn)
                val artist = cursor.getString(artistColumn) ?: context.getString(R.string.notFound)
                val genre = cursor.getString(albumColumn) ?: context.getString(R.string.notFound)
                val relativePath = cursor.getString(pathColumn)?.trimEnd('/') ?: ""

                // Ordner-Filter anwenden: Nur Songs aus ausgewählten Ordnern anzeigen
                if (selectedFolders == null || selectedFolders.isEmpty() ||
                    selectedFolders.contains(relativePath)
                ) {
                    // Datum konvertieren: Unix-Timestamp (Sekunden) -> "dd.MM.yyyy"
                    var release = cursor.getString(relDateColumn) ?: context.getString(R.string.notFound)
                    if (release != context.getString(R.string.notFound)) {
                        release = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(cursor.getLong(relDateColumn) * 1000L))
                    }

                    // Content-URI erstellen – wird für die Wiedergabe über MediaPlayer benötigt
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toLong()
                    )

                    mp3List += myAudio(
                        audioID = id,
                        audioTitle = name.substringBeforeLast(".", name),  // Dateiendung entfernen
                        audioArtist = artist,
                        audioGenre = genre,
                        audioPath = contentUri.toString(),
                        audioRelDate = release
                    )
                }
            }
        }
        return mp3List
    }

    /**
     * Prüft, ob ein Audioeintrag mit der gegebenen ID bereits in der Datenbank existiert.
     * Verwendet "SELECT 1" für maximale Effizienz (kein vollständiger Datensatz geladen).
     *
     * @param id Die zu prüfende Audio-ID
     * @return true, wenn der Eintrag existiert; false sonst
     */
    fun audioExists(id: Int): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE_AUDIO WHERE $AUDIO_ID = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    /**
     * Entfernt Audioeinträge aus der Datenbank, die nicht mehr im MediaStore vorhanden sind.
     * Schützt dabei Songs, die in benutzerdefinierten Playlists (ID != 1) liegen,
     * sowie manuell hinzugefügte Songs (ohne MediaStore content://-Pfad).
     *
     * @param currentIds Liste der aktuell im MediaStore vorhandenen Audio-IDs
     */
    fun removeDeletedAudios(currentIds: List<Int>) {
        if (currentIds.isEmpty()) return

        // Platzhalter für IN-Klausel dynamisch erzeugen
        val placeholders = currentIds.joinToString(",") { "?" }

        // Nur MediaStore-Songs löschen (content://media...), die nicht in benutzerdefinierten Playlists sind
        val query = "DELETE FROM $TABLE_AUDIO WHERE $AUDIO_ID NOT IN ($placeholders) " +
                "AND $AUDIO_SAVEPATH LIKE ? " +
                "AND $AUDIO_ID NOT IN (SELECT $FKPK_AUDIOPLAYLIST FROM $TABLE_PLAYAUDIO WHERE $FKPK_PLAYLISTAUDIO != 1)"

        val args = currentIds.map { it.toString() }.toTypedArray() + "content://media%"

        try {
            writableDatabase.execSQL(query, args)
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Löschen gelöschter Audios: $ex")
        }
    }

    /**
     * Fügt einen neuen Audioeintrag in die Datenbank ein.
     *
     * @param audio Das myAudio-Objekt mit allen Metadaten
     */
    fun addAudioToDatabase(audio: myAudio) {
        val container = ContentValues().apply {
            put(AUDIO_ID, audio.audioID)
            put(AUDIO_TITLE, audio.audioTitle)
            put(AUDIO_ARTIST, audio.audioArtist)
            put(AUDIO_SAVEPATH, audio.audioPath)
            put(AUDIO_RELDATE, audio.audioRelDate)
            put(AUDIO_GENRE, audio.audioGenre)
        }
        writableDatabase.insert(TABLE_AUDIO, null, container)
    }

    /**
     * Löscht einen Audioeintrag aus der Datenbank.
     * Die CASCADE-Regel in der Zwischentabelle löscht automatisch
     * alle Playlist-Verknüpfungen dieses Songs.
     *
     * @param track Der zu löschende Audioeintrag
     * @return true (Löschung immer als erfolgreich gemeldet)
     */
    fun deleteAudioEntry(track: myAudio): Boolean {
        try {
            writableDatabase.delete(TABLE_AUDIO, "$AUDIO_ID = ?", arrayOf(track.audioID.toString()))
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Löschen des Eintrags ${track.audioTitle}: $ex")
        }
        return true
    }

    /**
     * Aktualisiert die Metadaten eines bestehenden Audioeintrags in der Datenbank.
     * Die Audio-ID bleibt unverändert.
     *
     * @param track Der Audioeintrag mit den aktualisierten Daten
     * @return true (Aktualisierung immer als erfolgreich gemeldet)
     */
    fun editAudioEntry(track: myAudio): Boolean {
        try {
            val values = ContentValues().apply {
                put(AUDIO_TITLE, track.audioTitle)
                put(AUDIO_ARTIST, track.audioArtist)
                put(AUDIO_RELDATE, track.audioRelDate)
                put(AUDIO_GENRE, track.audioGenre)
                put(AUDIO_SAVEPATH, track.audioPath)
            }
            writableDatabase.update(TABLE_AUDIO, values, "$AUDIO_ID = ?", arrayOf(track.audioID.toString()))
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Ändern des Eintrags ${track.audioTitle}: $ex")
        }
        return true
    }

    /**
     * Verknüpft einen Audiotitel mit einer Playlist in der Zwischentabelle.
     * Erstellt einen neuen Eintrag in der AudioTrackPlaylist-Tabelle.
     *
     * @param audioID    Die ID des Audiotitels
     * @param playlistID Die ID der Ziel-Playlist
     */
    fun addAudioToPlaylist(audioID: Int, playlistID: Int) {
        val container = ContentValues().apply {
            put(FKPK_AUDIOPLAYLIST, audioID)
            put(FKPK_PLAYLISTAUDIO, playlistID)
        }
        writableDatabase.insert(TABLE_PLAYAUDIO, null, container)
    }

    /**
     * Entfernt die Verknüpfung eines Audiotitels mit einer Playlist.
     * Der Song selbst und die Playlist bleiben bestehen.
     *
     * @param audioID    Die ID des Audiotitels
     * @param playlistID Die ID der Playlist
     */
    fun removeAudioFromPlaylist(audioID: Int, playlistID: Int) {
        writableDatabase.delete(
            TABLE_PLAYAUDIO,
            "$FKPK_AUDIOPLAYLIST = ? AND $FKPK_PLAYLISTAUDIO = ?",
            arrayOf(audioID.toString(), playlistID.toString())
        )
    }

    //endregion

    //region CRUD für Playlist

    /**
     * Gibt alle Playlist-IDs zurück, denen ein bestimmter Audiotitel zugeordnet ist.
     * Wird für die Vorauswahl im Playlist-Selektor verwendet.
     *
     * @param audioID Die ID des Audiotitels
     * @return Liste der Playlist-IDs, in denen der Song enthalten ist
     */
    fun getPlaylistIDsForAudio(audioID: Int): List<Int> {
        val ids = mutableListOf<Int>()
        readableDatabase.rawQuery(
            "SELECT $FKPK_PLAYLISTAUDIO FROM $TABLE_PLAYAUDIO WHERE $FKPK_AUDIOPLAYLIST = ?",
            arrayOf(audioID.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                ids.add(cursor.getInt(0))
            }
        }
        return ids
    }

    /**
     * Liest alle Playlists aus der Datenbank und gibt sie als Liste zurück.
     *
     * @return Liste aller Playlists (inklusive der Standard-Playlist "Alle")
     */
    fun getAllPlaylistsFromDB(): List<myPlaylist> {
        val playlistList = mutableListOf<myPlaylist>()
        readableDatabase.rawQuery("SELECT * FROM $TABLE_PLAYLIST", null).use { cursor ->
            while (cursor.moveToNext()) {
                playlistList.add(
                    myPlaylist(
                        playlistID = cursor.getInt(0),
                        playlistTitle = cursor.getString(1)
                    )
                )
            }
        }
        return playlistList
    }

    /**
     * Fügt eine neue Playlist in die Datenbank ein.
     *
     * @param playlist Das myPlaylist-Objekt mit ID und Titel
     */
    fun addPlaylistToDatabase(playlist: myPlaylist) {
        val container = ContentValues().apply {
            put(PLAYLIST_ID, playlist.playlistID)
            put(PLAYLIST_TITLE, playlist.playlistTitle)
        }
        writableDatabase.insert(TABLE_PLAYLIST, null, container)
    }

    /**
     * Gibt alle Audiotitel einer bestimmten Playlist zurück.
     * Nutzt einen INNER JOIN über die Zwischentabelle, um die Songs zu ermitteln.
     *
     * @param playlistID Die ID der Playlist
     * @return Liste aller Audiotitel, die der Playlist zugeordnet sind
     */
    fun getAudiosByPlaylist(playlistID: Int): List<myAudio> {
        val audioList = mutableListOf<myAudio>()
        readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_AUDIO INNER JOIN $TABLE_PLAYAUDIO " +
                    "ON $TABLE_AUDIO.$AUDIO_ID = $TABLE_PLAYAUDIO.$FKPK_AUDIOPLAYLIST " +
                    "WHERE $TABLE_PLAYAUDIO.$FKPK_PLAYLISTAUDIO = ?",
            arrayOf(playlistID.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                audioList.add(
                    myAudio(
                        audioID = cursor.getInt(0),
                        audioTitle = cursor.getString(1),
                        audioArtist = cursor.getString(2),
                        audioGenre = cursor.getString(3),
                        audioRelDate = cursor.getString(4),
                        audioPath = cursor.getString(5)
                    )
                )
            }
        }
        return audioList
    }

    /**
     * Löscht eine Playlist aus der Datenbank.
     * Die Standard-Playlist "Alle" (ID 1) ist geschützt und kann nicht gelöscht werden.
     * CASCADE löscht automatisch alle Verknüpfungen in der Zwischentabelle.
     *
     * @param playlist Die zu löschende Playlist
     * @return true bei Erfolg, false wenn die "Alle"-Playlist betroffen ist
     */
    fun deletePlaylistEntry(playlist: myPlaylist): Boolean {
        if (playlist.playlistID == 1) {
            Log.e(TAG, "Die Alle-Playlist kann nicht gelöscht werden")
            return false
        }
        try {
            writableDatabase.delete(TABLE_PLAYLIST, "$PLAYLIST_ID = ?", arrayOf(playlist.playlistID.toString()))
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Löschen der Playlist ${playlist.playlistTitle}: $ex")
        }
        return true
    }

    /**
     * Aktualisiert den Titel einer Playlist.
     * Die "Alle"-Playlist (ID 1) ist geschützt und kann nicht umbenannt werden.
     *
     * @param playlist Die Playlist mit dem neuen Titel
     * @return true bei Erfolg, false wenn die "Alle"-Playlist betroffen ist oder ein Fehler auftritt
     */
    fun editPlaylistEntry(playlist: myPlaylist): Boolean {
        if (playlist.playlistID == 1) {
            Log.e(TAG, "Die Alle-Playlist kann nicht bearbeitet werden")
            return false
        }
        try {
            val values = ContentValues().apply {
                put(PLAYLIST_TITLE, playlist.playlistTitle)
            }
            writableDatabase.update(TABLE_PLAYLIST, values, "$PLAYLIST_ID = ?", arrayOf(playlist.playlistID.toString()))
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Ändern der Playlist ${playlist.playlistTitle}: $ex")
            return false
        }
        return true
    }

    //endregion
}

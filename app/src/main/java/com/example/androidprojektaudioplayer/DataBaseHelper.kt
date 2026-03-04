package com.example.androidprojektaudioplayer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

private const val TAG = "SoundioMusikPlayer"
private const val DATABASE_VERSION = 1
private const val DATABASE_NAME = "Musicman"

// Tabelle – Audio
private const val TABLE_AUDIO = "Audiotitel"
private const val AUDIO_ID = "AudioID"
private const val AUDIO_TITLE = "AudioTitle"
private const val AUDIO_ARTIST = "AudioArtist"
private const val AUDIO_GENRE = "AudioGenre"
private const val AUDIO_RELDATE = "AudioRelDate"
private const val AUDIO_SAVEPATH = "AudioPath"

// Tabelle – Playlist
private const val TABLE_PLAYLIST = "Playlist"
private const val PLAYLIST_ID = "PlaylistID"
private const val PLAYLIST_TITLE = "PlaylistTitle"

// Zwischentabelle
private const val TABLE_PLAYAUDIO = "AudioTrackPlaylist"
private const val FKPK_AUDIOPLAYLIST = "AudioPlaylist"
private const val FKPK_PLAYLISTAUDIO = "PlaylistAudio"

/**
 * SQLiteOpenHelper-Klasse, die die gesamte Datenbank-Logik kapselt.
 * Verwaltet drei Tabellen: Audio, Playlist und die Zwischentabelle AudioTrackPlaylist.
 * Bietet CRUD-Operationen für Audio und Playlists sowie MediaStore-Zugriff.
 */
class DataBaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase?) {
        db?.let {
            it.execSQL(
                "CREATE TABLE $TABLE_AUDIO(" +
                        "$AUDIO_ID INTEGER PRIMARY KEY, " +
                        "$AUDIO_TITLE TEXT, " +
                        "$AUDIO_ARTIST TEXT, " +
                        "$AUDIO_GENRE TEXT, " +
                        "$AUDIO_RELDATE TEXT, " +
                        "$AUDIO_SAVEPATH TEXT)"
            )
            it.execSQL(
                "CREATE TABLE $TABLE_PLAYLIST(" +
                        "$PLAYLIST_ID INTEGER PRIMARY KEY, " +
                        "$PLAYLIST_TITLE TEXT)"
            )
            it.execSQL(
                "CREATE TABLE $TABLE_PLAYAUDIO(" +
                        "$FKPK_AUDIOPLAYLIST INTEGER, " +
                        "$FKPK_PLAYLISTAUDIO INTEGER, " +
                        "PRIMARY KEY($FKPK_AUDIOPLAYLIST, $FKPK_PLAYLISTAUDIO), " +
                        "FOREIGN KEY($FKPK_AUDIOPLAYLIST) REFERENCES $TABLE_AUDIO($AUDIO_ID) ON DELETE CASCADE, " +
                        "FOREIGN KEY($FKPK_PLAYLISTAUDIO) REFERENCES $TABLE_PLAYLIST($PLAYLIST_ID) ON DELETE CASCADE)"
            )

            // Standard-Playlist "Alle" anlegen
            val container = ContentValues()
            container.put(PLAYLIST_TITLE, "Alle")
            it.insert(TABLE_PLAYLIST, null, container)
        } ?: Log.e(TAG, "Fehler: Datenbankinstanz ist null bei onCreate")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    override fun onOpen(db: SQLiteDatabase?) {
        super.onOpen(db)
        db?.setForeignKeyConstraintsEnabled(true)
    }

    /**
     * Liest alle Ordner aus, in denen Musikdateien auf dem Gerät gespeichert sind.
     * Nutzt RELATIVE_PATH (modern, ab API 29) statt des deprecated DATA-Feldes.
     */
    fun getAllAudioFolders(context: Context): List<String> {
        val folders = mutableSetOf<String>()
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
                    // Trailing "/" entfernen für konsistente Vergleiche
                    folders.add(relativePath.trimEnd('/'))
                }
            }
        }
        return folders.sorted()
    }

    //region Gemeinsame Hilfsmethoden

    /**
     * Ermittelt die nächste verfügbare ID für eine Tabelle.
     * Deduplizierte Methode – ersetzt getNextAvailableAudioID und getNextAvailablePlaylistID.
     */
    private fun getNextAvailableID(table: String, column: String): Int {
        readableDatabase.rawQuery("SELECT MAX($column) FROM $table", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) + 1 else 1
        }
    }

    fun getNextAvailableAudioID(): Int = getNextAvailableID(TABLE_AUDIO, AUDIO_ID)
    fun getNextAvailablePlaylistID(): Int = getNextAvailableID(TABLE_PLAYLIST, PLAYLIST_ID)

    //endregion

    //region CRUD für Audio

    /**
     * Liest alle Musikdateien aus dem MediaStore und gibt sie als Liste zurück.
     * Berücksichtigt den Ordner-Filter aus den SharedPreferences.
     * Nutzt RELATIVE_PATH statt des deprecated DATA-Feldes.
     */
    fun getAllMp3Files(context: Context): List<myAudio> {
        val mp3List = mutableListOf<myAudio>()

        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val selectedFolders = prefs.getStringSet("selectedFolders", null)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
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

                // Ordner-Filter anwenden
                if (selectedFolders == null || selectedFolders.isEmpty() ||
                    selectedFolders.contains(relativePath)
                ) {
                    var release = cursor.getString(relDateColumn) ?: context.getString(R.string.notFound)
                    if (release != context.getString(R.string.notFound)) {
                        release = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(cursor.getLong(relDateColumn) * 1000L))
                    }

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toLong()
                    )

                    mp3List += myAudio(
                        audioID = id,
                        audioTitle = name.substringBeforeLast(".", name),
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

    /** Prüft, ob ein Audioeintrag mit der gegebenen ID bereits in der Datenbank existiert. */
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
     * Nur Songs mit MediaStore-Pfad, die nicht in benutzerdefinierten Playlists liegen.
     */
    fun removeDeletedAudios(currentIds: List<Int>) {
        if (currentIds.isEmpty()) return

        val placeholders = currentIds.joinToString(",") { "?" }
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

    /** Fügt einen neuen Audioeintrag in die Datenbank ein. */
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
     * CASCADE-Regel löscht automatisch die Verknüpfungen in der Zwischentabelle.
     */
    fun deleteAudioEntry(track: myAudio): Boolean {
        try {
            writableDatabase.delete(TABLE_AUDIO, "$AUDIO_ID = ?", arrayOf(track.audioID.toString()))
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Löschen des Eintrags ${track.audioTitle}: $ex")
        }
        return true
    }

    /** Aktualisiert die Daten eines bestehenden Audioeintrags. */
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

    /** Verknüpft einen Audiotitel mit einer Playlist in der Zwischentabelle. */
    fun addAudioToPlaylist(audioID: Int, playlistID: Int) {
        val container = ContentValues().apply {
            put(FKPK_AUDIOPLAYLIST, audioID)
            put(FKPK_PLAYLISTAUDIO, playlistID)
        }
        writableDatabase.insert(TABLE_PLAYAUDIO, null, container)
    }

    /** Entfernt die Verknüpfung eines Audiotitels mit einer Playlist. */
    fun removeAudioFromPlaylist(audioID: Int, playlistID: Int) {
        writableDatabase.delete(
            TABLE_PLAYAUDIO,
            "$FKPK_AUDIOPLAYLIST = ? AND $FKPK_PLAYLISTAUDIO = ?",
            arrayOf(audioID.toString(), playlistID.toString())
        )
    }

    //endregion

    //region CRUD für Playlist

    /** Gibt alle Playlist-IDs zurück, denen ein bestimmter Audiotitel zugeordnet ist. */
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

    /** Liest alle Playlists aus der Datenbank. */
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

    /** Fügt eine neue Playlist in die Datenbank ein. */
    fun addPlaylistToDatabase(playlist: myPlaylist) {
        val container = ContentValues().apply {
            put(PLAYLIST_ID, playlist.playlistID)
            put(PLAYLIST_TITLE, playlist.playlistTitle)
        }
        writableDatabase.insert(TABLE_PLAYLIST, null, container)
    }

    /** Gibt alle Audiotitel einer bestimmten Playlist zurück (über INNER JOIN). */
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
     * Löscht eine Playlist. Die Standard-Playlist "Alle" (ID 1) kann nicht gelöscht werden.
     * CASCADE löscht automatisch alle Verknüpfungen in der Zwischentabelle.
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

    /** Aktualisiert den Titel einer Playlist. Die "Alle"-Playlist (ID 1) ist geschützt. */
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

package com.example.androidprojektaudioplayer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.res.TypedArrayUtils.getString
import java.io.File

private val TAG: String = "SoundioMusikPlayer"
private val DATABASE_VERSION: Int = 1;
private val DATABASE_NAME: String = "Musicman";

//region Tabelle - Audio
public val TABLE_AUDIO: String = "Audiotitel";
private val AUDIO_ID: String = "AudioID";
private val AUDIO_TITLE: String = "AudioTitle";
private val AUDIO_ARTIST: String = "AudioArtist";
private val AUDIO_GENRE: String = "AudioGenre";
private val AUDIO_RELDATE: String = "AudioRelDate";
private val AUDIO_SAVEPATH: String = "AudioPath";
//endregion

//region Tabelle - Playlist
private val TABLE_PLAYLIST: String = "Playlist";
private val PLAYLIST_ID: String = "PlaylistID";
private val PLAYLIST_TITLE: String = "PlaylistTitle";
//endregion

//region Zwischentabelle
private val TABLE_PLAYAUDIO: String = "AudioTrackPlaylist";
private val FKPK_AUDIOPLAYLIST: String = "AudioPlaylist";
private val FKPK_PLAYLISTAUDIO: String = "PlaylistAudio";
//endregion

class DataBaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase?) {
        val sqlStringCreateTableAudio: String =
            "CREATE TABLE $TABLE_AUDIO($AUDIO_ID INTEGER PRIMARY KEY, $AUDIO_TITLE TEXT, $AUDIO_ARTIST TEXT NULL, $AUDIO_GENRE TEXT NULL, $AUDIO_RELDATE TEXT NULL, $AUDIO_SAVEPATH TEXT);"

        val sqlStringCreateTablePlaylist: String =
            "CREATE TABLE $TABLE_PLAYLIST($PLAYLIST_ID INTEGER PRIMARY KEY, $PLAYLIST_TITLE TEXT)";

        val sqlStringCreateTableInter: String =
            "CREATE TABLE $TABLE_PLAYAUDIO($FKPK_AUDIOPLAYLIST INTEGER, $FKPK_PLAYLISTAUDIO INTEGER, " +
                    "PRIMARY KEY($FKPK_AUDIOPLAYLIST, $FKPK_PLAYLISTAUDIO), " +
                    "FOREIGN KEY($FKPK_AUDIOPLAYLIST) REFERENCES $TABLE_AUDIO($AUDIO_ID) ON DELETE CASCADE" +
                    ", FOREIGN KEY($FKPK_PLAYLISTAUDIO) REFERENCES $TABLE_PLAYLIST($PLAYLIST_ID) ON DELETE CASCADE)";

        if (db is SQLiteDatabase) {
            db.execSQL(sqlStringCreateTableAudio);
            db.execSQL(sqlStringCreateTablePlaylist);
            db.execSQL(sqlStringCreateTableInter);
            val container = ContentValues()
            container.put(PLAYLIST_TITLE, "Alle")
            db.insert(TABLE_PLAYLIST, null, container)

        } else {
            Log.e(TAG, "${this.javaClass.name} Fehler bei der Ausfuehrung von SQL Query");
        }
    }



    // Methode, um alle genutzten Ordner zu holen
    fun getAllAudioFolders(context: Context): List<String> {
        val folders = mutableSetOf<String>()

        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
        )

        cursor?.use {
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (it.moveToNext()) {
                val filePath = it.getString(dataColumn)
                val folder = File(filePath).parent

                if (folder != null) {
                    folders.add(folder)
                }
            }
        }

        return folders.sorted()  // Alphabetisch sortiert zurückgeben
    }

    //Methode zum Erneuern der db Version
    //Nicht verwendet
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    //Methode, um ForeignKeys zu aktivieren
    override fun onOpen(db: SQLiteDatabase?) {
        super.onOpen(db)
        if (db is SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }
    }

    //region CRUD für Audio

    //Methode zum Holen der MP3-Dateien
    fun getAllMp3Files(context: Context): List<myAudio> {
        val mp3List = mutableListOf<myAudio>()

        // Ausgewählte Ordner laden
        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val selectedFolders = prefs.getStringSet("selectedFolders", null)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,  // Album statt GENRE
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA  // Für Ordner-Filter
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val relDateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getInt(idColumn)
                val name = cursor.getString(nameColumn)
                val artist = cursor.getString(artistColumn) ?: context.getString(R.string.notFound)
                val album = cursor.getString(albumColumn) ?: context.getString(R.string.notFound)
                val path = cursor.getString(dataColumn)
                val folder = File(path).parent

                // Ordner-Filter
                if (selectedFolders == null || selectedFolders.isEmpty() ||
                    (folder != null && selectedFolders.contains(folder))) {

                    var release = cursor.getString(relDateColumn) ?: context.getString(R.string.notFound)
                    if (release != context.getString(R.string.notFound)) {
                        release = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(cursor.getLong(relDateColumn) * 1000L))
                    }

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toLong()
                    )

                    val audio = myAudio()
                    audio.audioID = id
                    audio.audioTitle = name
                    audio.audioArtist = artist
                    audio.audioGenre = album  // Album als Genre
                    audio.audioPath = contentUri.toString()
                    audio.audioRelDate = release

                    mp3List += audio
                }
            }
        }
        return mp3List
    }

    //Methode, um die nächste verfügbare ID aus der Datenbank zu bekommen
    fun getNextAvailableAudioID(): Int {
        val myQuery: String = "SELECT MAX(${AUDIO_ID}) FROM ${TABLE_AUDIO}";
        val db: SQLiteDatabase = readableDatabase;
        val myCursor: Cursor = db.rawQuery(myQuery, null);

        var maxID = -1;
        if (myCursor.moveToFirst()) {
            maxID = myCursor.getInt(0);
        }
        myCursor.close();
        return maxID + 1;
    }

    //Methode, um zu überprüfen, ob eine Audiodatei mit einer ID schon existiert
    fun audioExists(id: Int): Boolean {
        val query = "SELECT 1 FROM $TABLE_AUDIO WHERE $AUDIO_ID = $id"
        val db = readableDatabase
        val cursor = db.rawQuery(query, null)
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    //Methode, um gelöschte Audiodateien aus der Datenbank zu entfernen
    fun removeDeletedAudios(currentIds: List<Int>) {
        if (currentIds.isEmpty()) return
        val idList = currentIds.joinToString(",")
        writableDatabase.execSQL(
            "DELETE FROM $TABLE_AUDIO WHERE $AUDIO_ID NOT IN ($idList) AND $AUDIO_SAVEPATH LIKE 'content://media%'"
        )
    }

    //Methode für das Hinzufügen eines Eintrags zur Datenbank
    fun addAudioToDatabase(myAudio: myAudio) {
        val db: SQLiteDatabase = writableDatabase;
        val container: ContentValues = ContentValues();
        container.put(AUDIO_ID, myAudio.audioID);
        container.put(AUDIO_TITLE, myAudio.audioTitle);
        container.put(AUDIO_ARTIST, myAudio.audioArtist);
        container.put(AUDIO_SAVEPATH, myAudio.audioPath);
        container.put(AUDIO_RELDATE, myAudio.audioRelDate);
        container.put(AUDIO_GENRE, myAudio.audioGenre);

        db.insert(TABLE_AUDIO, null, container);
    }

    //Methode für das Löschen eines Eintrags aus der Datenbank
    //Nicht nötig, weil das Löschen von Audiodateien dem User selbst überlassen ist
    fun deleteAudioEntry(track: myAudio): Boolean {
        val db = writableDatabase
        try {
            db.execSQL("DELETE FROM $TABLE_PLAYAUDIO WHERE $FKPK_AUDIOPLAYLIST = ${track.audioID}")
            db.execSQL("DELETE FROM $TABLE_AUDIO WHERE $AUDIO_ID = ${track.audioID}")
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Loeschen des Eintrags ${track.audioTitle} aus Datenbank \n $ex")
        }
        return true
    }

    //Methode für das Bearbeiten eines Eintrags aus der Datenbank
    fun editAudioEntry(track: myAudio): Boolean {
        val editString: String =
            "UPDATE ${TABLE_AUDIO} SET ${AUDIO_TITLE} = '${track.audioTitle}', ${AUDIO_ARTIST} = '${track.audioArtist}'" +
                    ", ${AUDIO_RELDATE} = '${track.audioRelDate}', ${AUDIO_GENRE} = '${track.audioGenre}', ${AUDIO_SAVEPATH} = '${track.audioPath}' WHERE ${AUDIO_ID} = '${track.audioID}'";
        val db = writableDatabase;
        try {
            db.execSQL(editString);
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Aendern des Eintrags ${track.audioTitle} aus Datenbank \n $ex");
        }
        return true;
    }

    //Methode, um eine Audiodatei mit einer Playlist zu verknüpfen
    fun addAudioToPlaylist(audioID: Int, playlistID: Int) {
        val db = writableDatabase
        val container = ContentValues()
        container.put(FKPK_AUDIOPLAYLIST, audioID)
        container.put(FKPK_PLAYLISTAUDIO, playlistID)
        db.insert(TABLE_PLAYAUDIO, null, container);
    }

    //Methode, um eine Verknüpfung zwischen Audio und Playlist zu entfernen
    fun removeAudioFromPlaylist(audioID: Int, playlistID: Int) {
        writableDatabase.execSQL(
            "DELETE FROM $TABLE_PLAYAUDIO WHERE $FKPK_AUDIOPLAYLIST = $audioID AND $FKPK_PLAYLISTAUDIO = $playlistID"
        )
    }
    //endregion

    //region CRUD für Playlist

    //Methode um die nächst verfügbare ID zu bekommen
    fun getNextAvailablePlaylistID(): Int {
        val myQuery: String = "SELECT MAX(${PLAYLIST_ID}) FROM ${TABLE_PLAYLIST}";
        val db: SQLiteDatabase = readableDatabase;
        val myCursor: Cursor = db.rawQuery(myQuery, null);

        var maxID = -1;
        if (myCursor.moveToFirst()) {
            maxID = myCursor.getInt(0);
        }
        myCursor.close();
        return maxID + 1;
    }

    //Methode, um alle Playlist-IDs eines Audios zu holen
    fun getPlaylistIDsForAudio(audioID: Int): List<Int> {
        val ids = mutableListOf<Int>()
        val query = "SELECT $FKPK_PLAYLISTAUDIO FROM $TABLE_PLAYAUDIO WHERE $FKPK_AUDIOPLAYLIST = $audioID"
        val cursor = readableDatabase.rawQuery(query, null)
        if (cursor.moveToFirst()) {
            do {
                ids.add(cursor.getInt(0))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return ids
    }

    //Methode, um alle Playlists aus der Datenbank zu holen
    fun getAllPlaylistsFromDB(): List<myPlaylist> {
        val audioList = mutableListOf<myPlaylist>();
        val query = "SELECT * FROM $TABLE_PLAYLIST";
        val db = readableDatabase;
        val cursor = db.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                val audio = myPlaylist();
                audio.playlistID = cursor.getInt(0);
                audio.playlistTitle = cursor.getString(1);
                audioList.add(audio)
            } while (cursor.moveToNext())
        }
        cursor.close();
        return audioList;
    }

    //Methode für das Hinzufügen eines Eintrags zur Datenbank
    fun addPlaylistToDatabase(myPlaylist: myPlaylist) {
        val db: SQLiteDatabase = writableDatabase;
        val container: ContentValues = ContentValues();
        container.put(PLAYLIST_ID, myPlaylist.playlistID);
        container.put(PLAYLIST_TITLE, myPlaylist.playlistTitle);

        db.insert(TABLE_PLAYLIST, null, container);
    }

    //Methode, um alle Audiodateien einer Playlist zu holen
    fun getAudiosByPlaylist(playlistID: Int): List<myAudio> {
        val audioList = mutableListOf<myAudio>()
        val query = """
        SELECT * FROM $TABLE_AUDIO 
        INNER JOIN $TABLE_PLAYAUDIO 
        ON $TABLE_AUDIO.$AUDIO_ID = $TABLE_PLAYAUDIO.$FKPK_AUDIOPLAYLIST
        WHERE $TABLE_PLAYAUDIO.$FKPK_PLAYLISTAUDIO = $playlistID
    """
        val cursor = readableDatabase.rawQuery(query, null)
        if (cursor.moveToFirst()) {
            do {
                val audio = myAudio()
                audio.audioID = cursor.getInt(0)
                audio.audioTitle = cursor.getString(1)
                audio.audioArtist = cursor.getString(2)
                audio.audioGenre = cursor.getString(3)
                audio.audioRelDate = cursor.getString(4)
                audio.audioPath = cursor.getString(5)
                audioList.add(audio)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return audioList
    }

    //Methode für das Löschen eines Eintrags aus der Datenbank
    fun deletePlaylistEntry(playList: myPlaylist): Boolean {
        if (playList.playlistID == 1) {
            Log.e(TAG, "Die Alle-Playlist kann nicht gelöscht werden")
            return false
        }
        val deleteString: String =
            "DELETE FROM ${TABLE_PLAYLIST} WHERE ${PLAYLIST_ID} = ${playList.playlistID}";
        val db = writableDatabase;
        try {
            db.execSQL(deleteString);
        } catch (ex: Exception) {
            Log.e(
                TAG,
                "Fehler beim Loeschen des Eintrags ${playList.playlistTitle} aus Datenbank \n $ex"
            )
        }
        return true;
    }

    //Methode für das Bearbeiten eines Eintrags aus der Datenbank
    fun editPlaylistEntry(playList: myPlaylist): Boolean {
        if (playList.playlistID == 1) {
            Log.e(TAG, "Die Alle-Playlist kann nicht bearbeitet werden")
            return false
        }
        val editString: String =
            "UPDATE ${TABLE_PLAYLIST} SET ${PLAYLIST_TITLE} = '${playList.playlistTitle}' WHERE ${PLAYLIST_ID} = '${playList.playlistID}'";
        val db = writableDatabase;
        try {
            db.execSQL(editString);
        } catch (ex: Exception) {
            Log.e(
                TAG,
                "Fehler beim Aendern des Eintrags ${playList.playlistTitle} aus Datenbank \n $ex"
            );
        }
        return true;
    }
    //endregion

}
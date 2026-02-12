package com.example.androidprojektaudioplayer

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

private val TAG: String = "SoundioMusikPlayer"
private val DATABASE_VERSION: Int = 1;
private val DATABASE_NAME: String = "Musicman";

//region Tabelle - Audio
private val TABLE_AUDIO: String = "Audiotitel";
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
private val TABLE_PLAYAUDIO: String ="AudioTrackPlaylist";
private val FKPK_AUDIOPLAYLIST: String = "AudioPlaylist";
private val FKPK_PLAYLISTAUDIO: String ="PlaylistAudio";
//endregion

class DataBaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase?) {
        val sqlStringCreateTableAudio: String =
            "CREATE TABLE $TABLE_AUDIO($AUDIO_ID INTEGER PRIMARY KEY, $AUDIO_TITLE TEXT, $AUDIO_ARTIST TEXT NULL, $AUDIO_GENRE TEXT NULL, $AUDIO_RELDATE TEXT NULL, $AUDIO_SAVEPATH TEXT);"

        val sqlStringCreateTablePlaylist: String = "CREATE TABLE $TABLE_PLAYLIST($PLAYLIST_ID INTEGER PRIMARY KEY, $PLAYLIST_TITLE TEXT)";

        val sqlStringCreateTableInter: String = "CREATE TABLE $TABLE_PLAYAUDIO($FKPK_AUDIOPLAYLIST INTEGER, $FKPK_PLAYLISTAUDIO INTEGER, " +
                "PRIMARY KEY($FKPK_AUDIOPLAYLIST, $FKPK_PLAYLISTAUDIO), " +
                "FOREIGN KEY($FKPK_AUDIOPLAYLIST) REFERENCES $TABLE_AUDIO($AUDIO_ID)" +
                ", FOREIGN KEY($FKPK_PLAYLISTAUDIO) REFERENCES $TABLE_PLAYLIST($PLAYLIST_ID))";

        if (db is SQLiteDatabase) {
            db.execSQL(sqlStringCreateTableAudio);
            db.execSQL(sqlStringCreateTablePlaylist);
            db.execSQL(sqlStringCreateTableInter);
            db.setForeignKeyConstraintsEnabled(true);
        } else {
            Log.e(TAG, "${this.javaClass.name} Fehler bei der Ausfuehrung von SQL Query");
        }
    }


    //Methode zum Erneuern der db Version
    //Nicht verwendet
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        TODO("Not yet implemented")
    }

    //region CRUD für Audio

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
    fun deleteAudioEntry(track: myAudio): Boolean {
        val deleteString: String = "DELETE FROM ${TABLE_AUDIO} WHERE ${AUDIO_ID} = ${track.audioID}";
        val db = writableDatabase;
        try {
            db.execSQL(deleteString);
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Loeschen des Eintrags ${track.audioTitle} aus Datenbank \n $ex")
        }
        return true;
    }

    //Methode für das Bearbeiten eines Eintrags aus der Datenbank
    fun editAudioEntry(track: myAudio): Boolean {
        val editString: String = "UPDATE ${TABLE_AUDIO} SET ${AUDIO_TITLE} = '${track.audioTitle}', ${AUDIO_ARTIST} = '${track.audioArtist}'" +
                ", ${AUDIO_RELDATE} = '${track.audioRelDate}', ${AUDIO_GENRE} = '${track.audioGenre}', ${AUDIO_SAVEPATH} = '${track.audioPath}' WHERE ${AUDIO_ID} = '${track.audioID}'";
        val db = writableDatabase;
        try {
            db.execSQL(editString);
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Aendern des Eintrags ${track.audioTitle} aus Datenbank \n $ex");
        }
        return true;
    }
    //endregion

    //region CRUD für Playlist
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

    //Methode für das Hinzufügen eines Eintrags zur Datenbank
    fun addPlaylistToDatabase(myPlaylist: myPlaylist) {
        val db: SQLiteDatabase = writableDatabase;
        val container: ContentValues = ContentValues();
        container.put(PLAYLIST_ID, myPlaylist.playlistID);
        container.put(PLAYLIST_TITLE, myPlaylist.playlistTitle);

        db.insert(TABLE_PLAYLIST, null, container);
    }

    //Methode für das Löschen eines Eintrags aus der Datenbank
    fun deletePlaylistEntry(playList: myPlaylist): Boolean {
        val deleteString: String = "DELETE FROM ${TABLE_PLAYLIST} WHERE ${PLAYLIST_ID} = ${playList.playlistID}";
        val db = writableDatabase;
        try {
            db.execSQL(deleteString);
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Loeschen des Eintrags ${playList.playlistTitle} aus Datenbank \n $ex")
        }
        return true;
    }

    //Methode für das Bearbeiten eines Eintrags aus der Datenbank
    fun editPlaylistEntry(playList: myPlaylist): Boolean {
        val editString: String = "UPDATE ${TABLE_AUDIO} SET ${PLAYLIST_TITLE} = '${playList.playlistTitle}' WHERE ${PLAYLIST_ID} = '${playList.playlistID}'";
        val db = writableDatabase;
        try {
            db.execSQL(editString);
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Aendern des Eintrags ${playList.playlistTitle} aus Datenbank \n $ex");
        }
        return true;
    }
    //endregion

}
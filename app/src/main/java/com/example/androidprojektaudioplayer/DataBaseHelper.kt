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
private val TABLE_NAME: String = "Audiotitel";
private val COLUMN_ID: String = "AudioID";
private val COLUMN_TITLE: String = "AudioTitle";
private val COLUMN_ARTIST: String = "AudioArtist";
private val COLUMN_GENRE: String = "AudioGenre";
private val COLUMN_DATE: String = "AudioRelDate";
private val COLUMN_PATH: String = "AudioPath";

class DataBaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase?) {
        val sqlStringCreateTable: String =
            "CREATE TABLE $TABLE_NAME($COLUMN_ID INTEGER PRIMARY KEY, $COLUMN_TITLE TEXT, $COLUMN_ARTIST TEXT, $COLUMN_GENRE TEXT, $COLUMN_DATE TEXT, $COLUMN_PATH TEXT)";
        if (db is SQLiteDatabase) {
            db.execSQL(sqlStringCreateTable);
        } else {
            Log.e(TAG, "${this.javaClass.name} Fehler bei der Ausfuehrung von SQL Query");
        }
    }

    //Methode zum Erneuern der db Version
    //Nicht verwendet
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        TODO("Not yet implemented")
    }

    //Methode, um die nächste verfügbare ID aus der Datenbank zu bekommen
    fun getNextAvailableID(): Int {
        val myQuery: String = "SELECT MAX(${COLUMN_ID}) FROM ${TABLE_NAME}";
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
        container.put(COLUMN_ID, myAudio.audioID);
        container.put(COLUMN_TITLE, myAudio.audioTitle);
        container.put(COLUMN_ARTIST, myAudio.audioArtist);
        container.put(COLUMN_PATH, myAudio.audioPath);
        container.put(COLUMN_DATE, myAudio.audioRelDate);
        container.put(COLUMN_GENRE, myAudio.audioGenre);

        db.insert(TABLE_NAME, null, container);
    }

    //Methode für das Löschen eines Eintrags aus der Datenbank
    fun deleteEntry(track: myAudio): Boolean {
        val deleteString: String = "DELETE FROM ${TABLE_NAME} WHERE ${COLUMN_ID} = ${track.audioID}";
        val db = writableDatabase;
        try {
            db.execSQL(deleteString);
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Loeschen des Eintrags ${track.audioTitle} aus Datenbank \n $ex")
        }
        return true;
    }

    //Methode für das Bearbeiten eines Eintrags aus der Datenbank
    fun editEntry(track: myAudio): Boolean {
        val editString: String = "UPDATE ${TABLE_NAME} SET ${COLUMN_TITLE} = '${track.audioTitle}', ${COLUMN_ARTIST} = '${track.audioArtist}'" +
                ", ${COLUMN_DATE} = '${track.audioRelDate}', ${COLUMN_GENRE} = '${track.audioGenre}', ${COLUMN_PATH} = '${track.audioPath}' WHERE ${COLUMN_ID} = '${track.audioID}'";
        val db = writableDatabase;
        try {
            db.execSQL(editString);
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Aendern des Eintrags ${track.audioTitle} aus Datenbank \n $ex");
        }
        return true;
    }

}
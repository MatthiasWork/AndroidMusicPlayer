package com.example.androidprojektaudioplayer

import android.content.Context
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

}
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

/** Tag für Log-Ausgaben, um Meldungen dieser Klasse im Logcat leicht filtern zu können. */
private val TAG: String = "SoundioMusikPlayer"

/** Aktuelle Versionsnummer der Datenbank. Bei Änderungen am Schema wird diese erhöht. */
private val DATABASE_VERSION: Int = 1;

/** Name der SQLite-Datenbankdatei auf dem Gerät. */
private val DATABASE_NAME: String = "Musicman";

//region Tabelle - Audio
/** Tabellenname für die Audiotitel-Tabelle. */
public val TABLE_AUDIO: String = "Audiotitel";
/** Spaltenname für die eindeutige Audio-ID (Primärschlüssel). */
private val AUDIO_ID: String = "AudioID";
/** Spaltenname für den Titel des Audiotitels. */
private val AUDIO_TITLE: String = "AudioTitle";
/** Spaltenname für den Künstler/Interpreten. */
private val AUDIO_ARTIST: String = "AudioArtist";
/** Spaltenname für das Genre/Album. */
private val AUDIO_GENRE: String = "AudioGenre";
/** Spaltenname für das Veröffentlichungsdatum. */
private val AUDIO_RELDATE: String = "AudioRelDate";
/** Spaltenname für den Dateipfad/Content-URI. */
private val AUDIO_SAVEPATH: String = "AudioPath";
//endregion

//region Tabelle - Playlist
/** Tabellenname für die Playlist-Tabelle. */
private val TABLE_PLAYLIST: String = "Playlist";
/** Spaltenname für die eindeutige Playlist-ID (Primärschlüssel). */
private val PLAYLIST_ID: String = "PlaylistID";
/** Spaltenname für den Playlist-Titel. */
private val PLAYLIST_TITLE: String = "PlaylistTitle";
//endregion

//region Zwischentabelle
/** Tabellenname für die Verknüpfungstabelle zwischen Audio und Playlist (Many-to-Many). */
private val TABLE_PLAYAUDIO: String = "AudioTrackPlaylist";
/** Fremdschlüssel-Spalte, die auf die Audio-ID verweist. */
private val FKPK_AUDIOPLAYLIST: String = "AudioPlaylist";
/** Fremdschlüssel-Spalte, die auf die Playlist-ID verweist. */
private val FKPK_PLAYLISTAUDIO: String = "PlaylistAudio";
//endregion

/**
 * SQLiteOpenHelper-Klasse, die die gesamte Datenbank-Logik kapselt.
 * Verwaltet drei Tabellen: Audio, Playlist und die Zwischentabelle AudioTrackPlaylist.
 * Bietet CRUD-Operationen (Create, Read, Update, Delete) für Audio und Playlists,
 * sowie Methoden zum Lesen von MP3-Dateien aus dem MediaStore.
 *
 * @param context Der Application- oder Activity-Context für den Datenbankzugriff
 */
class DataBaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    /**
     * Wird beim ersten Erstellen der Datenbank aufgerufen.
     * Erstellt die drei Tabellen (Audio, Playlist, Zwischentabelle) und
     * fügt die Standard-Playlist "Alle" mit ID 1 ein.
     *
     * @param db Die SQLite-Datenbankinstanz, auf der die Tabellen erstellt werden
     */
    override fun onCreate(db: SQLiteDatabase?) {
        // SQL-Statement für die Audio-Tabelle mit optionalen Spalten für Artist, Genre und RelDate
        val sqlStringCreateTableAudio: String =
            "CREATE TABLE $TABLE_AUDIO($AUDIO_ID INTEGER PRIMARY KEY, $AUDIO_TITLE TEXT, $AUDIO_ARTIST TEXT NULL, $AUDIO_GENRE TEXT NULL, $AUDIO_RELDATE TEXT NULL, $AUDIO_SAVEPATH TEXT);"

        // SQL-Statement für die Playlist-Tabelle
        val sqlStringCreateTablePlaylist: String =
            "CREATE TABLE $TABLE_PLAYLIST($PLAYLIST_ID INTEGER PRIMARY KEY, $PLAYLIST_TITLE TEXT)";

        // SQL-Statement für die Zwischentabelle mit zusammengesetztem Primärschlüssel
        // und Fremdschlüsseln mit CASCADE-Löschung (Verknüpfungen werden automatisch entfernt)
        val sqlStringCreateTableInter: String =
            "CREATE TABLE $TABLE_PLAYAUDIO($FKPK_AUDIOPLAYLIST INTEGER, $FKPK_PLAYLISTAUDIO INTEGER, " +
                    "PRIMARY KEY($FKPK_AUDIOPLAYLIST, $FKPK_PLAYLISTAUDIO), " +
                    "FOREIGN KEY($FKPK_AUDIOPLAYLIST) REFERENCES $TABLE_AUDIO($AUDIO_ID) ON DELETE CASCADE" +
                    ", FOREIGN KEY($FKPK_PLAYLISTAUDIO) REFERENCES $TABLE_PLAYLIST($PLAYLIST_ID) ON DELETE CASCADE)";

        if (db is SQLiteDatabase) {
            db.execSQL(sqlStringCreateTableAudio);
            db.execSQL(sqlStringCreateTablePlaylist);
            db.execSQL(sqlStringCreateTableInter);

            // Standard-Playlist "Alle" anlegen, die alle Songs enthält und nicht löschbar ist
            val container = ContentValues()
            container.put(PLAYLIST_TITLE, "Alle")
            db.insert(TABLE_PLAYLIST, null, container)

        } else {
            Log.e(TAG, "${this.javaClass.name} Fehler bei der Ausfuehrung von SQL Query");
        }
    }

    /**
     * Liest alle Ordner aus, in denen Musikdateien auf dem Gerät gespeichert sind.
     * Nutzt den MediaStore, um alle Musikdateien zu finden, und extrahiert deren Ordnerpfade.
     * Duplikate werden automatisch durch die Verwendung eines Sets entfernt.
     *
     * @param context Der Context für den Zugriff auf den ContentResolver
     * @return Eine alphabetisch sortierte Liste aller Ordnerpfade mit Musikdateien
     */
    fun getAllAudioFolders(context: Context): List<String> {
        val folders = mutableSetOf<String>();

        // Nur den Dateipfad aus dem MediaStore abfragen
        val projection = arrayOf(MediaStore.Audio.Media.DATA);
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0";

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
        );

        cursor?.use {
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

            while (it.moveToNext()) {
                val filePath = it.getString(dataColumn);
                // Den übergeordneten Ordner des Dateipfads extrahieren
                val folder = File(filePath).parent;

                if (folder != null) {
                    folders.add(folder);
                }
            }
        }

        return folders.sorted();  // Alphabetisch sortiert zurückgeben
    }

    /**
     * Wird aufgerufen, wenn die Datenbankversion erhöht wird (Schema-Migration).
     * Aktuell nicht implementiert, da keine Migration nötig ist.
     *
     * @param db         Die Datenbankinstanz
     * @param oldVersion Die alte Versionsnummer
     * @param newVersion Die neue Versionsnummer
     */
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    /**
     * Wird jedes Mal aufgerufen, wenn die Datenbank geöffnet wird.
     * Aktiviert die Fremdschlüssel-Unterstützung in SQLite, damit CASCADE-Regeln
     * bei DELETE und UPDATE korrekt funktionieren.
     *
     * @param db Die geöffnete Datenbankinstanz
     */
    override fun onOpen(db: SQLiteDatabase?) {
        super.onOpen(db)
        if (db is SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }
    }

    //region CRUD für Audio

    /**
     * Liest alle Musikdateien aus dem MediaStore des Geräts und gibt sie als Liste zurück.
     * Berücksichtigt dabei den Ordner-Filter aus den SharedPreferences:
     * Nur Songs aus ausgewählten Ordnern werden zurückgegeben.
     *
     * Für jeden gefundenen Song wird ein myAudio-Objekt erstellt mit:
     * - ID aus dem MediaStore
     * - Titel (ohne Dateiendung)
     * - Künstler, Album, Datum
     * - Content-URI für die Wiedergabe
     *
     * @param context Der Context für den Zugriff auf MediaStore und SharedPreferences
     * @return Liste aller gefilterten Musikdateien als myAudio-Objekte
     */
    fun getAllMp3Files(context: Context): List<myAudio> {
        val mp3List = mutableListOf<myAudio>()

        // Ausgewählte Ordner aus den Einstellungen laden (null = alle Ordner)
        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val selectedFolders = prefs.getStringSet("selectedFolders", null)

        // Spalten, die aus dem MediaStore abgefragt werden
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )

        // Nur Dateien laden, die als Musik markiert sind
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"  // Alphabetisch nach Dateiname sortieren
        )?.use { cursor ->
            // Spalten-Indizes ermitteln für schnelleren Zugriff in der Schleife
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
            val relDateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getInt(idColumn)
                val name = cursor.getString(nameColumn)
                val artist = cursor.getString(artistColumn) ?: context.getString(R.string.notFound)
                val album = cursor.getString(albumColumn) ?: context.getString(R.string.notFound)
                val path = cursor.getString(dataColumn)
                val folder = File(path).parent

                // Ordner-Filter anwenden: Song nur aufnehmen, wenn Ordner ausgewählt ist
                // oder kein Filter gesetzt ist
                if (selectedFolders == null || selectedFolders.isEmpty() ||
                    (folder != null && selectedFolders.contains(folder))) {

                    // Datum vom Unix-Timestamp in lesbares Format konvertieren
                    var release = cursor.getString(relDateColumn) ?: context.getString(R.string.notFound)
                    if (release != context.getString(R.string.notFound)) {
                        release = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(cursor.getLong(relDateColumn) * 1000L))
                    }

                    // Content-URI erstellen, über die der MediaPlayer die Datei abspielen kann
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toLong()
                    )

                    // myAudio-Objekt befüllen
                    val audio = myAudio()
                    audio.audioID = id
                    // Dateiendungen vom Titel entfernen, damit nur der Songname angezeigt wird
                    audio.audioTitle = name.removeSuffix(".mp3").removeSuffix(".wav")
                        .removeSuffix(".m4a").removeSuffix(".aac").removeSuffix(".ogg").removeSuffix(".flac");
                    audio.audioArtist = artist
                    audio.audioGenre = album  // Album wird als Genre-Feld verwendet
                    audio.audioPath = contentUri.toString()
                    audio.audioRelDate = release

                    mp3List += audio
                }
//                for (i in 0 until cursor.columnCount) {
//                    android.util.Log.i("MediaStoreDebug", "${cursor.getColumnName(i)} = ${cursor.getString(i)}")
//                }
            }
        }
        return mp3List
    }

    /**
     * Ermittelt die nächste verfügbare Audio-ID für einen neuen Eintrag.
     * Sucht die höchste vorhandene ID in der Audio-Tabelle und gibt diese + 1 zurück.
     *
     * @return Die nächste freie Audio-ID
     */
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

    /**
     * Prüft, ob ein Audioeintrag mit der gegebenen ID bereits in der Datenbank existiert.
     * Wird verwendet, um doppelte Einträge beim Synchronisieren mit dem MediaStore zu vermeiden.
     *
     * @param id Die zu prüfende Audio-ID
     * @return true, wenn der Eintrag existiert; false, wenn nicht
     */
    fun audioExists(id: Int): Boolean {
        val query = "SELECT 1 FROM $TABLE_AUDIO WHERE $AUDIO_ID = $id"
        val db = readableDatabase
        val cursor = db.rawQuery(query, null)
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    /**
     * Entfernt Audioeinträge aus der Datenbank, die nicht mehr im MediaStore vorhanden sind.
     * Es werden nur Songs gelöscht, die:
     * 1. Nicht in der aktuellen MediaStore-Liste enthalten sind
     * 2. Einen MediaStore-Pfad haben (content://media...)
     * 3. NICHT in benutzerdefinierten Playlists (also nur in "Alle") vorhanden sind
     *
     * @param currentIds Liste der aktuell im MediaStore vorhandenen Audio-IDs
     */
    fun removeDeletedAudios(currentIds: List<Int>) {
        if (currentIds.isEmpty()) return

        // Platzhalter für die SQL-IN-Klausel erstellen (?, ?, ?, ...)
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

    /**
     * Fügt einen neuen Audioeintrag in die Audio-Tabelle der Datenbank ein.
     * Alle Felder des myAudio-Objekts werden als Spaltenwerte übernommen.
     *
     * @param myAudio Das myAudio-Objekt mit den Daten des neuen Eintrags
     */
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

    /**
     * Löscht einen Audioeintrag und alle zugehörigen Playlist-Verknüpfungen aus der Datenbank.
     * Zuerst werden die Einträge in der Zwischentabelle entfernt, dann der Audio-Eintrag selbst.
     *
     * @param track Der zu löschende Audiotitel
     * @return true, wenn die Operation durchgeführt wurde
     */
    fun deleteAudioEntry(track: myAudio): Boolean {
        val db = writableDatabase
        try {
            // Zuerst alle Verknüpfungen in der Zwischentabelle löschen
            db.execSQL("DELETE FROM $TABLE_PLAYAUDIO WHERE $FKPK_AUDIOPLAYLIST = ${track.audioID}")
            // Dann den Audioeintrag selbst löschen
            db.execSQL("DELETE FROM $TABLE_AUDIO WHERE $AUDIO_ID = ${track.audioID}")
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Loeschen des Eintrags ${track.audioTitle} aus Datenbank \n $ex")
        }
        return true
    }

    /**
     * Aktualisiert die Daten eines bestehenden Audioeintrags in der Datenbank.
     * Die Audio-ID wird als Identifikator verwendet, alle anderen Felder werden überschrieben.
     *
     * @param track Das myAudio-Objekt mit den aktualisierten Daten
     * @return true, wenn die Operation durchgeführt wurde
     */
    fun editAudioEntry(track: myAudio): Boolean {
        val db = writableDatabase;
        try {
            val value = ContentValues();
            value.put(AUDIO_TITLE, track.audioTitle);
            value.put(AUDIO_ARTIST, track.audioArtist)
            value.put(AUDIO_RELDATE, track.audioRelDate);
            value.put(AUDIO_GENRE, track.audioGenre);
            value.put(AUDIO_SAVEPATH, track.audioPath);
            db.update(TABLE_AUDIO, value, "$AUDIO_ID = ?", arrayOf(track.audioID.toString()))
        } catch (ex: Exception) {
            Log.e(TAG, "Fehler beim Aendern des Eintrags ${track.audioTitle} aus Datenbank \n $ex");
        }
        return true;
    }

    /**
     * Erstellt eine Verknüpfung zwischen einem Audiotitel und einer Playlist
     * in der Zwischentabelle. Dadurch wird der Song der angegebenen Playlist hinzugefügt.
     *
     * @param audioID    Die ID des Audiotitels
     * @param playlistID Die ID der Playlist, zu der der Song hinzugefügt werden soll
     */
    fun addAudioToPlaylist(audioID: Int, playlistID: Int) {
        val db = writableDatabase
        val container = ContentValues()
        container.put(FKPK_AUDIOPLAYLIST, audioID)
        container.put(FKPK_PLAYLISTAUDIO, playlistID)
        db.insert(TABLE_PLAYAUDIO, null, container);
    }

    /**
     * Entfernt die Verknüpfung zwischen einem Audiotitel und einer Playlist
     * aus der Zwischentabelle. Der Song wird damit aus der Playlist entfernt,
     * bleibt aber in der Audio-Tabelle erhalten.
     *
     * @param audioID    Die ID des Audiotitels
     * @param playlistID Die ID der Playlist, aus der der Song entfernt werden soll
     */
    fun removeAudioFromPlaylist(audioID: Int, playlistID: Int) {
        writableDatabase.execSQL(
            "DELETE FROM $TABLE_PLAYAUDIO WHERE $FKPK_AUDIOPLAYLIST = $audioID AND $FKPK_PLAYLISTAUDIO = $playlistID"
        )
    }
    //endregion

    //region CRUD für Playlist

    /**
     * Ermittelt die nächste verfügbare Playlist-ID für einen neuen Eintrag.
     * Sucht die höchste vorhandene ID in der Playlist-Tabelle und gibt diese + 1 zurück.
     *
     * @return Die nächste freie Playlist-ID
     */
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

    /**
     * Gibt alle Playlist-IDs zurück, denen ein bestimmter Audiotitel zugeordnet ist.
     * Wird verwendet, um im Playlist-Auswahldialog anzuzeigen, in welchen Playlists
     * ein Song bereits enthalten ist.
     *
     * @param audioID Die ID des Audiotitels
     * @return Liste der Playlist-IDs, in denen der Song enthalten ist
     */
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

    /**
     * Liest alle Playlists aus der Datenbank und gibt sie als Liste zurück.
     * Dazu gehört auch die Standard-Playlist "Alle" mit ID 1.
     *
     * @return Liste aller myPlaylist-Objekte aus der Datenbank
     */
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

    /**
     * Fügt eine neue Playlist in die Playlist-Tabelle der Datenbank ein.
     *
     * @param myPlaylist Das myPlaylist-Objekt mit ID und Titel der neuen Playlist
     */
    fun addPlaylistToDatabase(myPlaylist: myPlaylist) {
        val db: SQLiteDatabase = writableDatabase;
        val container: ContentValues = ContentValues();
        container.put(PLAYLIST_ID, myPlaylist.playlistID);
        container.put(PLAYLIST_TITLE, myPlaylist.playlistTitle);

        db.insert(TABLE_PLAYLIST, null, container);
    }

    /**
     * Gibt alle Audiotitel zurück, die einer bestimmten Playlist zugeordnet sind.
     * Verwendet einen INNER JOIN zwischen der Audio-Tabelle und der Zwischentabelle,
     * um nur die Songs der angegebenen Playlist zu liefern.
     *
     * @param playlistID Die ID der Playlist, deren Songs geladen werden sollen
     * @return Liste der myAudio-Objekte, die zur Playlist gehören
     */
    fun getAudiosByPlaylist(playlistID: Int): List<myAudio> {
        val audioList = mutableListOf<myAudio>()
        val query = "SELECT * FROM $TABLE_AUDIO INNER JOIN $TABLE_PLAYAUDIO ON $TABLE_AUDIO.$AUDIO_ID = $TABLE_PLAYAUDIO.$FKPK_AUDIOPLAYLIST WHERE $TABLE_PLAYAUDIO.$FKPK_PLAYLISTAUDIO = $playlistID";
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

    /**
     * Löscht eine Playlist aus der Datenbank.
     * Die Standard-Playlist "Alle" (ID 1) kann nicht gelöscht werden.
     * Durch die CASCADE-Regel in der Zwischentabelle werden alle Verknüpfungen
     * automatisch mit gelöscht.
     *
     * @param playList Die zu löschende Playlist
     * @return true bei Erfolg, false wenn es die "Alle"-Playlist ist
     */
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

    /**
     * Aktualisiert den Titel einer bestehenden Playlist in der Datenbank.
     * Die Standard-Playlist "Alle" (ID 1) kann nicht bearbeitet werden.
     *
     * @param playList Das myPlaylist-Objekt mit der ID und dem neuen Titel
     * @return true bei Erfolg, false wenn es die "Alle"-Playlist ist oder ein Fehler auftritt
     */
    fun editPlaylistEntry(playList: myPlaylist): Boolean {
        if (playList.playlistID == 1) {
            Log.e(TAG, "Die Alle-Playlist kann nicht bearbeitet werden")
            return false
        }
        val db = writableDatabase;
        try {
            val values = ContentValues();
            values.put(PLAYLIST_TITLE, playList.playlistTitle);
            db.update(TABLE_PLAYLIST, values, "$PLAYLIST_ID = ?", arrayOf(playList.playlistID.toString()));
        } catch (ex: Exception) {
            Log.e(
                TAG,
                "Fehler beim Aendern des Eintrags ${playList.playlistTitle} aus Datenbank \n $ex"
            );
            return false;
        }
        return true;
    }
    //endregion

}

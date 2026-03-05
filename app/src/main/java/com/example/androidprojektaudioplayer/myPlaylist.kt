package com.example.androidprojektaudioplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Datenklasse, die eine einzelne Playlist repräsentiert.
 * Die Playlist mit ID 1 ist die Standard-Playlist "Alle", die alle Songs enthält
 * und weder gelöscht noch umbenannt werden darf.
 *
 * @property playlistID    Eindeutige ID der Playlist (Primärschlüssel in der Datenbank)
 * @property playlistTitle Anzeigename der Playlist
 */
@Parcelize
data class myPlaylist(
    var playlistID: Int = 0,
    var playlistTitle: String = ""
) : Parcelable {

    /**
     * Gibt den Playlist-Titel als String-Darstellung zurück.
     * Wird z.B. in der Playlist-Leiste oder bei Log-Ausgaben verwendet.
     */
    override fun toString(): String = playlistTitle
}

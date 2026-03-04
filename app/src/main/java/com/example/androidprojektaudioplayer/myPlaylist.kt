package com.example.androidprojektaudioplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Datenklasse, die eine einzelne Playlist repräsentiert.
 * Die Playlist mit ID 1 ist die Standard-Playlist "Alle" und darf nicht gelöscht werden.
 */
@Parcelize
data class myPlaylist(
    var playlistID: Int = 0,
    var playlistTitle: String = ""
) : Parcelable {
    override fun toString(): String = playlistTitle
}

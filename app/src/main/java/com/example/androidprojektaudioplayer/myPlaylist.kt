package com.example.androidprojektaudioplayer

import java.io.Serializable

/**
 * Datenklasse, die eine einzelne Playlist repräsentiert.
 * Implementiert Serializable, damit Objekte zwischen Activities übergeben werden können.
 */
class myPlaylist : Serializable {

    /**
     * Eindeutige ID der Playlist in der Datenbank.
     * Die Playlist mit ID 1 ist die Standard-Playlist "Alle" und darf nicht gelöscht werden.
     * Nur positive Werte (>= 0) werden akzeptiert.
     */
    var playlistID: Int = 0
        get() = field
        set(value) {
            if (value >= 0)
                field = value;
        }

    /**
     * Name/Titel der Playlist (z. B. "Meine Favoriten").
     * Leere Strings werden ignoriert, um den Titel nicht versehentlich zu löschen.
     */
    var playlistTitle: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    /**
     * Default-Konstruktor: Erstellt eine leere Playlist mit Standardwerten.
     * Wird z. B. beim Auslesen aus der Datenbank verwendet.
     */
    constructor() {}

    /**
     * Parametrisierter Konstruktor: Erstellt eine Playlist mit den angegebenen Werten.
     *
     * @param ex_playlistID    Eindeutige ID der Playlist
     * @param ex_playlistTitle Titel/Name der Playlist
     */
    constructor(
        ex_playlistID: Int,
        ex_playlistTitle: String
    ) {
        playlistID = ex_playlistID;
        playlistTitle = ex_playlistTitle;
    }

    /**
     * Gibt den Titel der Playlist als String-Darstellung zurück.
     *
     * @return Der Titel der Playlist
     */
    override fun toString(): String {
        return playlistTitle;
    }
}

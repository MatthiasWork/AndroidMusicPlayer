package com.example.androidprojektaudioplayer

import java.io.Serializable

/**
 * Datenklasse für die Zwischentabelle (Many-to-Many-Beziehung) zwischen Audio und Playlist.
 * Ein Eintrag verknüpft genau einen Audiotitel mit genau einer Playlist.
 * Implementiert Serializable für die Übergabe zwischen Komponenten.
 */
class myAudioPlaylist : Serializable {

    /**
     * Fremdschlüssel, der auf die Audio-ID verweist (Primärschlüssel aus der Audio-Tabelle).
     * Nur positive Werte (>= 0) werden akzeptiert.
     */
    var audioPlaylistFKPK: Int = 0
        get() = field
        set(value) {
            if (value >= 0)
                field = value;
        }

    /**
     * Fremdschlüssel, der auf die Playlist-ID verweist (Primärschlüssel aus der Playlist-Tabelle).
     * Nur positive Werte (>= 0) werden akzeptiert.
     */
    var playlistAudioFKPK: Int = 0
        get() = field
        set(value) {
            if (value >= 0)
                field = value;
        }

    /**
     * Default-Konstruktor: Erstellt ein leeres Verknüpfungsobjekt mit Standardwerten.
     */
    constructor() {}

    /**
     * Parametrisierter Konstruktor: Erstellt eine Verknüpfung zwischen Audio und Playlist.
     *
     * @param ex_audioPlaylistFKPK  Die ID des Audiotitels (Fremdschlüssel)
     * @param ex_playlistAudioFKPK  Die ID der Playlist (Fremdschlüssel)
     */
    constructor(
        ex_audioPlaylistFKPK: Int,
        ex_playlistAudioFKPK: Int
    ) {
        audioPlaylistFKPK = ex_audioPlaylistFKPK;
        playlistAudioFKPK = ex_playlistAudioFKPK;
    }

    /**
     * Gibt eine lesbare String-Darstellung der Verknüpfung zurück,
     * bestehend aus Audio-ID und Playlist-ID.
     *
     * @return String mit AudioID und PlaylistID
     */
    override fun toString(): String {
        return "AudioID: $audioPlaylistFKPK \n PlaylistID: $playlistAudioFKPK";
    }
}

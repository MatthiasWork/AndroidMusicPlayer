package com.example.androidprojektaudioplayer

import java.io.Serializable

/**
 * Datenklasse für die Zwischentabelle (Many-to-Many-Beziehung) zwischen Audio und Playlist.
 * Ein Eintrag verknüpft genau einen Audiotitel mit genau einer Playlist.
 * Implementiert Serializable für die Übergabe zwischen Android-Komponenten.
 *
 * Hinweis: Im Gegensatz zu myAudio und myPlaylist wird hier bewusst kein
 * @Parcelize verwendet, da diese Klasse eigene Setter-Validierung besitzt.
 */
class myAudioPlaylist : Serializable {

    /**
     * Fremdschlüssel, der auf die Audio-ID verweist (Primärschlüssel aus der Audio-Tabelle).
     * Der Setter akzeptiert nur nicht-negative Werte (>= 0), um ungültige IDs zu verhindern.
     */
    var audioPlaylistFKPK: Int = 0
        get() = field
        set(value) {
            if (value >= 0)
                field = value;
        }

    /**
     * Fremdschlüssel, der auf die Playlist-ID verweist (Primärschlüssel aus der Playlist-Tabelle).
     * Der Setter akzeptiert nur nicht-negative Werte (>= 0), um ungültige IDs zu verhindern.
     */
    var playlistAudioFKPK: Int = 0
        get() = field
        set(value) {
            if (value >= 0)
                field = value;
        }

    /**
     * Default-Konstruktor: Erstellt ein leeres Verknüpfungsobjekt mit Standardwerten (0, 0).
     */
    constructor() {}

    /**
     * Parametrisierter Konstruktor: Erstellt eine Verknüpfung zwischen einem Audiotitel und einer Playlist.
     *
     * @param ex_audioPlaylistFKPK  Die ID des Audiotitels (Fremdschlüssel auf Audio-Tabelle)
     * @param ex_playlistAudioFKPK  Die ID der Playlist (Fremdschlüssel auf Playlist-Tabelle)
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
     * bestehend aus Audio-ID und Playlist-ID. Nützlich für Debugging und Logging.
     *
     * @return String im Format "AudioID: X \n PlaylistID: Y"
     */
    override fun toString(): String {
        return "AudioID: $audioPlaylistFKPK \n PlaylistID: $playlistAudioFKPK";
    }
}

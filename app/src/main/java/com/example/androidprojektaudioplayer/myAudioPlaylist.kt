package com.example.androidprojektaudioplayer

import java.io.Serializable

//Klasse für die Zwischentabelle für Audio und Playlist
class myAudioPlaylist : Serializable {
    var audioPlaylistFKPK: Int = 0
        get() = field
        set(value) {
            if (value >= 0)
                field = value;
        }

    var playlistAudioFKPK: Int = 0
        get() = field
        set(value) {
            if (value >= 0)
                field = value;
        }

    //Default-Konstruktor
    constructor() {}

    //Konstruktor
    constructor(
        ex_audioPlaylistFKPK: Int,
        ex_playlistAudioFKPK: Int
    ) {
        audioPlaylistFKPK = ex_audioPlaylistFKPK;
        playlistAudioFKPK = ex_playlistAudioFKPK;
    }

    override fun toString(): String {
        return "AudioID: $audioPlaylistFKPK \n PlaylistID: $playlistAudioFKPK";
    }
}
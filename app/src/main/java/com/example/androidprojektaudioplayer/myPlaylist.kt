package com.example.androidprojektaudioplayer

import java.io.Serializable

//Klasse für die einzelnen Playlisteinträge
class myPlaylist : Serializable {
    var playlistID: Int = 0
        get() = field
        set(value) {
            if (value >= 0)
                field = value;
        }

    var playlistTitle: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    //Default-Konstruktor
    constructor() {}

    //Konstruktor
    constructor(
        ex_playlistID: Int,
        ex_playlistTitle: String
    ) {
        playlistID = ex_playlistID;
        playlistTitle = ex_playlistTitle;
    }

    override fun toString(): String {
        return playlistTitle;
    }
}
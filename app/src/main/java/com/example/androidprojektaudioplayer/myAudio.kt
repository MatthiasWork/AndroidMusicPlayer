package com.example.androidprojektaudioplayer

import java.io.Serializable

//Klasse für die einzelnen Audioeinträge
class myAudio : Serializable{
    var audioID: Int = 0
        get() = field
        set(value) {
            if (value >= 0)
                field = value;
        }

    var audioTitle: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    var audioArtist: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    var audioGenre: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    var audioPath: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    var audioRelDate: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    //Default-Konstruktor
    constructor() {}

    //Konstruktor
    constructor(
        ex_audioID: Int,
        ex_audioTitle: String,
        ex_audioArtist: String,
        ex_audioGenre: String,
        ex_audioPath: String,
        ex_audioRelDate: String
    ) {
        audioID = ex_audioID;
        audioTitle = ex_audioTitle;
        audioArtist = ex_audioArtist;
        audioGenre = ex_audioGenre;
        audioPath = ex_audioPath;
        audioRelDate = ex_audioRelDate;
    }

    override fun toString(): String {
        return audioTitle;
    }
}
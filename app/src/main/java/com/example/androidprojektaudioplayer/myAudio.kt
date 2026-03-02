package com.example.androidprojektaudioplayer

import java.io.Serializable

/**
 * Datenklasse, die einen einzelnen Audioeintrag (Song) repräsentiert.
 * Implementiert Serializable, damit Objekte dieser Klasse zwischen Activities
 * oder über Intents übergeben werden können.
 */
class myAudio : Serializable{

    /**
     * Eindeutige ID des Audiotitels (entspricht der MediaStore-ID oder der Datenbank-ID).
     * Nur positive Werte (>= 0) werden akzeptiert, um ungültige IDs zu verhindern.
     */
    var audioID: Int = 0
        get() = field
        set(value) {
            if (value >= 0)
                field = value;
        }

    /**
     * Titel des Audiotitels (z. B. Songname).
     * Leere Strings werden ignoriert, damit der Titel nicht versehentlich gelöscht wird.
     */
    var audioTitle: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    /**
     * Name des Künstlers/Interpreten.
     * Leere Strings werden ignoriert, um einen bestehenden Wert nicht zu überschreiben.
     */
    var audioArtist: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    /**
     * Genre bzw. Album des Audiotitels.
     * Leere Strings werden ignoriert.
     */
    var audioGenre: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    /**
     * Dateipfad bzw. Content-URI zur Audiodatei.
     * Wird benötigt, um den Song über den MediaPlayer abspielen zu können.
     * Leere Strings werden ignoriert.
     */
    var audioPath: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    /**
     * Veröffentlichungsdatum des Audiotitels im Format "dd.MM.yyyy".
     * Leere Strings werden ignoriert.
     */
    var audioRelDate: String = ""
        get() = field
        set(value) {
            if (value.isNotEmpty())
                field = value;
        }

    /**
     * Default-Konstruktor: Erstellt ein leeres myAudio-Objekt mit Standardwerten.
     * Wird z. B. beim Auslesen aus der Datenbank verwendet, wo die Werte
     * anschließend einzeln gesetzt werden.
     */
    constructor() {}

    /**
     * Parametrisierter Konstruktor: Erstellt ein myAudio-Objekt mit allen Werten auf einmal.
     *
     * @param ex_audioID       Eindeutige ID des Audiotitels
     * @param ex_audioTitle    Titel des Songs
     * @param ex_audioArtist   Name des Künstlers
     * @param ex_audioGenre    Genre/Album des Songs
     * @param ex_audioPath     Dateipfad oder Content-URI zur Audiodatei
     * @param ex_audioRelDate  Veröffentlichungsdatum als String
     */
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

    /**
     * Gibt den Titel des Audiotitels als String-Darstellung zurück.
     * Wird z. B. bei Log-Ausgaben oder in Listen automatisch verwendet.
     *
     * @return Der Titel des Songs
     */
    override fun toString(): String {
        return audioTitle;
    }
}

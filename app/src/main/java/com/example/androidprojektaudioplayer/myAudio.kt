package com.example.androidprojektaudioplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Datenklasse, die einen einzelnen Audioeintrag (Song) repräsentiert.
 * Nutzt @Parcelize, um die Parcelable-Implementierung automatisch zu generieren –
 * das ermöglicht die effiziente Übergabe zwischen Activities und Services.
 *
 * @property audioID      Eindeutige ID des Audioeintrags (entspricht der MediaStore-ID oder der DB-ID)
 * @property audioTitle   Titel des Songs (ohne Dateiendung)
 * @property audioArtist  Name des Künstlers/Interpreten
 * @property audioGenre   Genre des Songs (z.B. "Rock", "Pop")
 * @property audioPath    URI-Pfad zur Audiodatei (content://-URI aus dem MediaStore oder benutzerdefiniert)
 * @property audioRelDate Veröffentlichungsdatum im Format "dd.MM.yyyy"
 */
@Parcelize
data class myAudio(
    var audioID: Int = 0,
    var audioTitle: String = "",
    var audioArtist: String = "",
    var audioGenre: String = "",
    var audioPath: String = "",
    var audioRelDate: String = ""
) : Parcelable {

    /**
     * Gibt den Songtitel als String-Darstellung zurück.
     * Wird z.B. in Spinner-Elementen oder bei Log-Ausgaben verwendet.
     */
    override fun toString(): String = audioTitle
}

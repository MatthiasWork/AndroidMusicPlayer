package com.example.androidprojektaudioplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Datenklasse, die einen einzelnen Audioeintrag (Song) repräsentiert.
 * Nutzt @Parcelize statt Serializable für bessere Performance auf Android.
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
    override fun toString(): String = audioTitle
}

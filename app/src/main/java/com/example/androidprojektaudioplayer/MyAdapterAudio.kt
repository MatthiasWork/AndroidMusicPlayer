package com.example.androidprojektaudioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * RecyclerView-Adapter für die Anzeige von Audiotiteln in der Song-Liste.
 * Jeder Eintrag zeigt Titel, Künstler, Genre und Datum an.
 * Über ein Kontextmenü können Songs bearbeitet, zu Playlists hinzugefügt
 * oder aus der aktuellen Playlist entfernt werden.
 */
class MyAdapterAudio(
    private val audioList: MutableList<myAudio>,
    private val contextExt: Context,
    private val currentPlaylistID: Int,
    private val onTrackClicked: (myAudio) -> Unit,
    private val onTrackEdited: (myAudio) -> Unit,
    private val onAddToPlaylist: (myAudio) -> Unit,
    private val onRemoveFromPlaylist: (myAudio) -> Unit
) : RecyclerView.Adapter<MyAdapterAudio.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.audiocard, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentTrack = audioList[position]

        holder.tvTitle.text = currentTrack.audioTitle
        holder.tvGenre.text = currentTrack.audioGenre
        holder.tvArtist.text = currentTrack.audioArtist
        holder.tvDate.text = currentTrack.audioRelDate

        // Klick auf die Card: Song abspielen
        holder.itemView.setOnClickListener { onTrackClicked(currentTrack) }

        // Klick auf "Mehr"-Button: Kontextmenü anzeigen
        holder.btnMore.setOnClickListener {
            val inflater = LayoutInflater.from(contextExt)
            val popupView = inflater.inflate(R.layout.popupwindow_track, null)

            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            // "Aus Playlist entfernen" nur bei benutzerdefinierten Playlists anzeigen
            popupView.findViewById<MaterialButton>(R.id.btnRemoveFromPlaylistPopUp).visibility =
                if (currentPlaylistID == 1) View.GONE else View.VISIBLE

            // Song bearbeiten
            popupView.findViewById<MaterialButton>(R.id.btnEditAudioPopUp).setOnClickListener {
                popupWindow.dismiss()
                showEditPopup(inflater, holder, currentTrack, position)
            }

            // Song zu Playlist hinzufügen
            popupView.findViewById<MaterialButton>(R.id.btnAddToPlaylistPopUp).setOnClickListener {
                onAddToPlaylist(currentTrack)
                popupWindow.dismiss()
            }

            // Song aus Playlist entfernen
            popupView.findViewById<MaterialButton>(R.id.btnRemoveFromPlaylistPopUp).setOnClickListener {
                onRemoveFromPlaylist(currentTrack)
                popupWindow.dismiss()
            }

            popupWindow.showAsDropDown(holder.btnMore)
        }
    }

    /** Zeigt das Edit-PopupWindow mit vorausgefüllten Feldern an. */
    private fun showEditPopup(
        inflater: LayoutInflater,
        holder: MyViewHolder,
        currentTrack: myAudio,
        position: Int
    ) {
        val editView = inflater.inflate(R.layout.popupwindow_track_edit, null)
        val editPopup = PopupWindow(
            editView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Felder vorausfüllen
        editView.findViewById<TextInputEditText>(R.id.etEditAudioTitle).setText(currentTrack.audioTitle)
        editView.findViewById<TextInputEditText>(R.id.etEditAudioArtist).setText(currentTrack.audioArtist)
        editView.findViewById<TextInputEditText>(R.id.etEditAudioGenre).setText(currentTrack.audioGenre)
        editView.findViewById<TextInputEditText>(R.id.etEditAudioDate).setText(currentTrack.audioRelDate)

        // DatePicker über die gemeinsame Hilfsmethode
        editView.findViewById<TextInputLayout>(R.id.tilEditAudioDate)?.setEndIconOnClickListener {
            val dateField = editView.findViewById<TextInputEditText>(R.id.etEditAudioDate)
            DatePickerUtils.showDatePicker(contextExt, dateField)
        }

        // Speichern-Button
        editView.findViewById<MaterialButton>(R.id.btnSaveEditAudio).setOnClickListener {
            currentTrack.audioTitle = editView.findViewById<TextInputEditText>(R.id.etEditAudioTitle).text.toString()
            currentTrack.audioArtist = editView.findViewById<TextInputEditText>(R.id.etEditAudioArtist).text.toString()
            currentTrack.audioGenre = editView.findViewById<TextInputEditText>(R.id.etEditAudioGenre).text.toString()
            currentTrack.audioRelDate = editView.findViewById<TextInputEditText>(R.id.etEditAudioDate).text.toString()
            onTrackEdited(currentTrack)
            notifyItemChanged(position)
            editPopup.dismiss()
        }

        editPopup.showAtLocation(holder.itemView, android.view.Gravity.CENTER, 0, 0)
    }

    override fun getItemCount(): Int = audioList.size

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvGenre: TextView = itemView.findViewById(R.id.tvGenre)
        val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val btnMore: ImageButton = itemView.findViewById(R.id.btnMore)
        val card: View = itemView.findViewById(R.id.songID)
    }
}

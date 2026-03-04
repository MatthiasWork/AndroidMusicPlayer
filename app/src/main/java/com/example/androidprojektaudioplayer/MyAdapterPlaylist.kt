package com.example.androidprojektaudioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * RecyclerView-Adapter für die Anzeige der Playlists in der horizontalen Playlist-Leiste.
 * Ein Klick wählt die Playlist aus, ein langer Klick öffnet Bearbeiten/Löschen.
 * Die "Alle"-Playlist (ID 1) ist geschützt.
 */
class MyAdapterPlaylist(
    private val playlistList: MutableList<myPlaylist>,
    private val contextExt: Context,
    private val onPlaylistClicked: (myPlaylist) -> Unit,
    private val onPlaylistEdited: (myPlaylist, String) -> Unit,
    private val onPlaylistDeleted: (myPlaylist) -> Unit
) : RecyclerView.Adapter<MyAdapterPlaylist.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlistcard, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentPlaylist = playlistList[position]
        holder.tvTitle.text = currentPlaylist.playlistTitle

        holder.itemView.setOnClickListener { onPlaylistClicked(currentPlaylist) }

        holder.itemView.setOnLongClickListener {
            if (currentPlaylist.playlistID == 1) {
                AlertDialog.Builder(contextExt)
                    .setMessage("Diese Playlist kann man nicht bearbeiten oder löschen.")
                    .setTitle("Hinweis!")
                    .setCancelable(true)
                    .show()
                true
            } else {
                showPlaylistPopup(holder, currentPlaylist, position)
                true
            }
        }
    }

    /** Zeigt das Kontextmenü zum Bearbeiten/Löschen einer Playlist. */
    private fun showPlaylistPopup(holder: MyViewHolder, playlist: myPlaylist, position: Int) {
        val inflater = LayoutInflater.from(contextExt)
        val popupView = inflater.inflate(R.layout.popupwindow_longplaylist, null)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Bearbeiten
        popupView.findViewById<MaterialButton>(R.id.btnEditPopUp).setOnClickListener {
            popupWindow.dismiss()
            showEditPopup(inflater, holder, playlist, position)
        }

        // Löschen
        popupView.findViewById<MaterialButton>(R.id.btnDeletePopUp).setOnClickListener {
            onPlaylistDeleted(playlist)
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(holder.itemView)
    }

    /** Zeigt das Edit-Popup zum Umbenennen einer Playlist. */
    private fun showEditPopup(
        inflater: LayoutInflater,
        holder: MyViewHolder,
        playlist: myPlaylist,
        position: Int
    ) {
        val editView = inflater.inflate(R.layout.popupwindow_editfollowup, null)
        val editPopup = PopupWindow(
            editView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        editView.findViewById<TextInputEditText>(R.id.etNewPlaylistName)
            .setText(playlist.playlistTitle)

        editView.findViewById<MaterialButton>(R.id.btnSaveEditPopUp).setOnClickListener {
            val newName = editView.findViewById<TextInputEditText>(R.id.etNewPlaylistName)
                .text.toString()
            if (newName.isNotEmpty()) {
                playlist.playlistTitle = newName
                onPlaylistEdited(playlist, newName)
                notifyItemChanged(position)
                editPopup.dismiss()
            }
        }

        editPopup.showAsDropDown(holder.itemView)
    }

    override fun getItemCount(): Int = playlistList.size

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvplaylistName)
        val card: View = itemView.findViewById(R.id.playlistID)
    }
}

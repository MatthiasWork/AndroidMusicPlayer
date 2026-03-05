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
 *
 * Interaktionen:
 * - Einfacher Klick: Playlist auswählen und Songs anzeigen
 * - Langer Klick: Kontextmenü mit Bearbeiten/Löschen öffnen
 *
 * Die "Alle"-Playlist (ID 1) ist geschützt – bei langem Klick wird ein Hinweis-Dialog angezeigt.
 *
 * @param playlistList      Die anzuzeigende Liste der Playlists
 * @param contextExt        Der Context für LayoutInflater und Dialoge
 * @param onPlaylistClicked Callback: Playlist wurde ausgewählt
 * @param onPlaylistEdited  Callback: Playlist wurde umbenannt (Playlist-Objekt, neuer Name)
 * @param onPlaylistDeleted Callback: Playlist soll gelöscht werden
 */
class MyAdapterPlaylist(
    private val playlistList: MutableList<myPlaylist>,
    private val contextExt: Context,
    private val onPlaylistClicked: (myPlaylist) -> Unit,
    private val onPlaylistEdited: (myPlaylist, String) -> Unit,
    private val onPlaylistDeleted: (myPlaylist) -> Unit
) : RecyclerView.Adapter<MyAdapterPlaylist.MyViewHolder>() {

    /**
     * Erstellt einen neuen ViewHolder mit dem playlistcard-Layout.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlistcard, parent, false)
        return MyViewHolder(view)
    }

    /**
     * Bindet eine Playlist an den ViewHolder.
     * Registriert Click- und LongClick-Listener.
     */
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentPlaylist = playlistList[position]
        holder.tvTitle.text = currentPlaylist.playlistTitle

        // Einfacher Klick: Playlist auswählen und Songs laden
        holder.itemView.setOnClickListener { onPlaylistClicked(currentPlaylist) }

        // Langer Klick: Kontextmenü oder Hinweis anzeigen
        holder.itemView.setOnLongClickListener {
            if (currentPlaylist.playlistID == 1) {
                // "Alle"-Playlist ist geschützt – Hinweis-Dialog anzeigen
                AlertDialog.Builder(contextExt)
                    .setMessage("Diese Playlist kann man nicht bearbeiten oder löschen.")
                    .setTitle("Hinweis!")
                    .setCancelable(true)
                    .show()
                true
            } else {
                // Benutzerdefinierte Playlist: Kontextmenü mit Bearbeiten/Löschen
                showPlaylistPopup(holder, currentPlaylist, position)
                true  // true = Event wurde konsumiert (kein weiterer Click)
            }
        }
    }

    /**
     * Zeigt das Kontextmenü (PopupWindow) zum Bearbeiten oder Löschen einer Playlist.
     *
     * @param holder   Der ViewHolder des angeklickten Eintrags
     * @param playlist Die betroffene Playlist
     * @param position Die Position in der Liste
     */
    private fun showPlaylistPopup(holder: MyViewHolder, playlist: myPlaylist, position: Int) {
        val inflater = LayoutInflater.from(contextExt)
        val popupView = inflater.inflate(R.layout.popupwindow_longplaylist, null)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Button: Playlist bearbeiten – öffnet das Edit-Popup
        popupView.findViewById<MaterialButton>(R.id.btnEditPopUp).setOnClickListener {
            popupWindow.dismiss()
            showEditPopup(inflater, holder, playlist, position)
        }

        // Button: Playlist löschen – delegiert an die MainActivity
        popupView.findViewById<MaterialButton>(R.id.btnDeletePopUp).setOnClickListener {
            onPlaylistDeleted(playlist)
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(holder.itemView)
    }

    /**
     * Zeigt das Bearbeitungs-Popup zum Umbenennen einer Playlist.
     * Das Eingabefeld ist mit dem aktuellen Namen vorausgefüllt.
     *
     * @param inflater Der LayoutInflater für das Popup-Layout
     * @param holder   Der ViewHolder des bearbeiteten Eintrags
     * @param playlist Die zu bearbeitende Playlist
     * @param position Die Position in der Liste (für notifyItemChanged)
     */
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

        // Eingabefeld mit dem aktuellen Playlist-Namen vorausfüllen
        editView.findViewById<TextInputEditText>(R.id.etNewPlaylistName)
            .setText(playlist.playlistTitle)

        // Speichern-Button: Neuen Namen übernehmen, falls nicht leer
        editView.findViewById<MaterialButton>(R.id.btnSaveEditPopUp).setOnClickListener {
            val newName = editView.findViewById<TextInputEditText>(R.id.etNewPlaylistName)
                .text.toString()
            if (newName.isNotEmpty()) {
                playlist.playlistTitle = newName
                onPlaylistEdited(playlist, newName)  // Callback an die Activity
                notifyItemChanged(position)           // UI aktualisieren
                editPopup.dismiss()
            }
        }

        editPopup.showAsDropDown(holder.itemView)
    }

    /** Gibt die Anzahl der Playlists zurück. */
    override fun getItemCount(): Int = playlistList.size

    /**
     * ViewHolder für einen einzelnen Playlist-Eintrag.
     * Enthält den Playlist-Titel und die Card-Referenz.
     */
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvplaylistName)
        val card: View = itemView.findViewById(R.id.playlistID)
    }
}

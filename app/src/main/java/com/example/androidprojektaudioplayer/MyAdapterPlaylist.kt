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
 * Jeder Eintrag zeigt den Playlist-Namen an. Ein Klick wählt die Playlist aus,
 * ein langer Klick öffnet ein Kontextmenü zum Bearbeiten oder Löschen.
 * Die Standard-Playlist "Alle" (ID 1) ist geschützt und kann nicht bearbeitet oder gelöscht werden.
 *
 * @param playlistList       Die Liste der anzuzeigenden Playlists
 * @param contextExt         Der Context für Layout-Inflation und Dialoge
 * @param onPlaylistClicked  Callback, wenn eine Playlist angeklickt wird (Songs laden)
 * @param onPlaylistEdited   Callback, wenn eine Playlist umbenannt wurde
 * @param onPlaylistDeleted  Callback, wenn eine Playlist gelöscht werden soll
 */
class MyAdapterPlaylist(
    private val playlistList: MutableList<myPlaylist>,
    private val contextExt: Context,
    private val onPlaylistClicked: (myPlaylist) -> Unit,
    private val onPlaylistEdited: (myPlaylist, String) -> Unit,
    private val onPlaylistDeleted: (myPlaylist) -> Unit
) : RecyclerView.Adapter<MyAdapterPlaylist.MyViewHolder>() {

    /**
     * Erstellt einen neuen ViewHolder, indem das playlistcard-Layout inflated wird.
     *
     * @param parent   Die übergeordnete ViewGroup (der RecyclerView)
     * @param viewType Der View-Typ (hier nicht differenziert)
     * @return Ein neuer MyViewHolder mit dem inflateten Layout
     */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MyAdapterPlaylist.MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlistcard, parent, false)
        return MyViewHolder(view, contextExt)
    }

    /**
     * Bindet die Daten einer Playlist an den ViewHolder an der gegebenen Position.
     * Setzt den Playlist-Namen und konfiguriert zwei Listener:
     * - Einfacher Klick: Playlist auswählen und Songs laden
     * - Langer Klick: Kontextmenü mit Bearbeiten/Löschen anzeigen
     *   (oder Hinweis bei der "Alle"-Playlist)
     *
     * @param holder   Der ViewHolder, an den die Daten gebunden werden
     * @param position Die Position der Playlist in der Liste
     */
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentPlaylist = playlistList[position]
        holder.tvTitle.text = currentPlaylist.playlistTitle

        // Einfacher Klick: Playlist auswählen – die zugehörigen Songs werden geladen
        holder.itemView.setOnClickListener {
            onPlaylistClicked(currentPlaylist)
        }

        // Langer Klick: Kontextmenü anzeigen oder Hinweis bei geschützter "Alle"-Playlist
        holder.itemView.setOnLongClickListener {
            if (currentPlaylist.playlistID == 1) {
                // "Alle"-Playlist ist geschützt: AlertDialog mit Hinweis anzeigen
                val builder = AlertDialog.Builder(contextExt);
                builder.setMessage("Diese Playlist kann man nicht bearbeiten oder löschen.");
                builder.setTitle("Hinweis!");
                builder.setCancelable(true);
                builder.create();
                builder.show();
                true;
            } else {
                // Benutzerdefinierte Playlist: PopupWindow mit Bearbeiten/Löschen anzeigen
                val inflater = LayoutInflater.from(contextExt)
                val popupView = inflater.inflate(R.layout.popupwindow_longplaylist, null)

                val popupWindow = PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
                )

                // Listener: Playlist bearbeiten – öffnet ein weiteres PopupWindow
                // mit einem Eingabefeld für den neuen Namen
                popupView.findViewById<MaterialButton>(R.id.btnEditPopUp).setOnClickListener {
                    popupWindow.dismiss();
                    val editView = inflater.inflate(R.layout.popupwindow_editfollowup, null)
                    val editPopup = PopupWindow(
                        editView,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                    )

                    // Eingabefeld mit dem aktuellen Playlist-Namen vorausfüllen
                    editView.findViewById<TextInputEditText>(R.id.etNewPlaylistName)
                        .setText(currentPlaylist.playlistTitle)

                    // Listener: Neuen Namen speichern
                    editView.findViewById<MaterialButton>(R.id.btnSaveEditPopUp)
                        .setOnClickListener {
                            val newName =
                                editView.findViewById<TextInputEditText>(R.id.etNewPlaylistName)
                                    .text.toString()
                            if (newName.isNotEmpty()) {
                                currentPlaylist.playlistTitle = newName
                                onPlaylistEdited(currentPlaylist, newName);
                                notifyItemChanged(position);
                                editPopup.dismiss()
                            }
                        }

                    editPopup.showAsDropDown(holder.itemView)
                }

                // Listener: Playlist löschen – ruft den Callback auf und schließt das Popup
                popupView.findViewById<MaterialButton>(R.id.btnDeletePopUp).setOnClickListener {
                    onPlaylistDeleted(currentPlaylist);
                    popupWindow.dismiss();
                }

                popupWindow.showAsDropDown(holder.itemView)
                true
            }
        }
    }

    /**
     * Gibt die Anzahl der Playlists in der Liste zurück.
     *
     * @return Die Anzahl der Playlist-Einträge
     */
    override fun getItemCount(): Int {
        return playlistList.size
    }

    /**
     * ViewHolder-Klasse, die die UI-Elemente einer einzelnen Playlist-Card hält.
     * Enthält ein TextView für den Playlist-Namen und eine Referenz auf die gesamte Card.
     *
     * @param itemView   Das inflated View der playlistcard
     * @param contextExt Der Context (wird für eventuelle Erweiterungen bereitgehalten)
     */
    class MyViewHolder(itemView: View, contextExt: Context) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvplaylistName)
        val card: View = itemView.findViewById(R.id.playlistID)
    }
}

package com.example.androidprojektaudioplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView-Adapter für die Playlist-Auswahl beim Hinzufügen eines Songs zu Playlists.
 * Wird im BottomSheet-Dialog der MainActivity angezeigt.
 *
 * Zeigt alle benutzerdefinierten Playlists (ohne die "Alle"-Playlist) mit Checkbox an.
 * Bereits zugewiesene Playlists sind vorausgewählt.
 *
 * Die Auswahl wird in [selectedPlaylists] gespeichert und von der MainActivity
 * nach Bestätigung verarbeitet (hinzufügen/entfernen von Verknüpfungen).
 *
 * @param playlistList Die Liste der benutzerdefinierten Playlists (ohne "Alle")
 */
class MyAdapterPlaylistSelect(
    private val playlistList: MutableList<myPlaylist>
) : RecyclerView.Adapter<MyAdapterPlaylistSelect.MyViewHolder>() {

    /** IDs der aktuell ausgewählten Playlists – wird von der Activity ausgelesen. */
    val selectedPlaylists = mutableSetOf<Int>()

    /**
     * Erstellt einen neuen ViewHolder mit dem playlist_selectorcard-Layout.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.playlist_selectorcard, parent, false)
        return MyViewHolder(view)
    }

    /**
     * Bindet eine Playlist an den ViewHolder.
     * Setzt den Playlist-Namen und den Checkbox-Zustand.
     */
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentPlaylist = playlistList[position]
        holder.tvPlaylistName.text = currentPlaylist.playlistTitle

        // WICHTIG: Listener vor dem Setzen des Checked-Zustands entfernen,
        // um Fehlauslösungen beim RecyclerView-Recycling zu vermeiden.
        holder.cbPlaylist.setOnCheckedChangeListener(null)
        holder.cbPlaylist.isChecked = selectedPlaylists.contains(currentPlaylist.playlistID)

        // Neuen Listener setzen: Auswahl in selectedPlaylists aktualisieren
        holder.cbPlaylist.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedPlaylists.add(currentPlaylist.playlistID)
            else selectedPlaylists.remove(currentPlaylist.playlistID)
        }
    }

    /** Gibt die Anzahl der benutzerdefinierten Playlists zurück. */
    override fun getItemCount(): Int = playlistList.size

    /**
     * ViewHolder für einen einzelnen Playlist-Auswahl-Eintrag.
     * Enthält eine Checkbox und den Playlist-Namen.
     */
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cbPlaylist: CheckBox = itemView.findViewById(R.id.cbPlaylist)
        val tvPlaylistName: TextView = itemView.findViewById(R.id.tvPlaylistName)
    }
}

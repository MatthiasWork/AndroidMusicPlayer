package com.example.androidprojektaudioplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView-Adapter für die Playlist-Auswahl beim Hinzufügen eines Songs zu Playlists.
 * Zeigt alle benutzerdefinierten Playlists (ohne "Alle") mit einer Checkbox an.
 * Bereits zugeordnete Playlists sind vorausgewählt. Der Benutzer kann
 * Playlists an- und abwählen, bevor er die Auswahl bestätigt.
 *
 * @param playlistList Die Liste der zur Auswahl stehenden Playlists
 */
class MyAdapterPlaylistSelect(
    private val playlistList: MutableList<myPlaylist>
) : RecyclerView.Adapter<MyAdapterPlaylistSelect.MyViewHolder>() {

    /**
     * Set, das die IDs der aktuell ausgewählten Playlists speichert.
     * Wird von der MainActivity nach Bestätigung gelesen, um die Zuordnungen
     * in der Datenbank zu aktualisieren (hinzufügen/entfernen).
     */
    val selectedPlaylists = mutableSetOf<Int>()

    /**
     * Erstellt einen neuen ViewHolder, indem das playlist_selectorcard-Layout inflated wird.
     *
     * @param parent   Die übergeordnete ViewGroup (der RecyclerView)
     * @param viewType Der View-Typ (hier nicht differenziert)
     * @return Ein neuer MyViewHolder mit dem inflateten Layout
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.playlist_selectorcard, parent, false)
        return MyViewHolder(view)
    }

    /**
     * Bindet eine Playlist an den ViewHolder an der gegebenen Position.
     * Setzt den Playlist-Namen und den Checkbox-Zustand basierend auf der Auswahl.
     * Beim Ändern der Checkbox wird die Playlist-ID zum selectedPlaylists-Set
     * hinzugefügt oder daraus entfernt.
     *
     * Wichtig: Der Listener wird vor dem Setzen des Checkbox-Zustands entfernt und
     * danach neu gesetzt, um ungewollte Callbacks beim Recycling von ViewHoldern zu vermeiden.
     *
     * @param holder   Der ViewHolder, an den die Daten gebunden werden
     * @param position Die Position der Playlist in der Liste
     */
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentPlaylist = playlistList[position]
        holder.tvPlaylistName.text = currentPlaylist.playlistTitle

        // Listener temporär entfernen, um Fehlauslösungen beim Recycling zu vermeiden
        holder.cbPlaylist.setOnCheckedChangeListener(null)

        // Checkbox-Zustand setzen: angehakt, wenn Playlist bereits ausgewählt ist
        holder.cbPlaylist.isChecked = selectedPlaylists.contains(currentPlaylist.playlistID)

        // Neuen Listener setzen: Playlist-ID bei Auswahl/Abwahl zum Set hinzufügen/entfernen
        holder.cbPlaylist.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedPlaylists.add(currentPlaylist.playlistID)
            } else {
                selectedPlaylists.remove(currentPlaylist.playlistID)
            }
        }
    }

    /**
     * Gibt die Anzahl der zur Auswahl stehenden Playlists zurück.
     *
     * @return Die Anzahl der Playlist-Einträge
     */
    override fun getItemCount(): Int {
        return playlistList.size
    }

    /**
     * ViewHolder-Klasse, die die UI-Elemente einer einzelnen Playlist-Auswahl-Card hält.
     * Enthält eine CheckBox für die Auswahl und ein TextView für den Playlist-Namen.
     *
     * @param itemView Das inflated View der playlist_selectorcard
     */
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cbPlaylist: CheckBox = itemView.findViewById(R.id.cbPlaylist)
        val tvPlaylistName: TextView = itemView.findViewById(R.id.tvPlaylistName)
    }
}

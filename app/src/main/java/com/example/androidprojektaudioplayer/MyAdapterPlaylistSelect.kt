package com.example.androidprojektaudioplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MyAdapterPlaylistSelect(
    private val playlistList: MutableList<myPlaylist>
) : RecyclerView.Adapter<MyAdapterPlaylistSelect.MyViewHolder>() {

    // Speichert welche Playlists ausgewählt sind
    val selectedPlaylists = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.playlist_selectorcard, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentPlaylist = playlistList[position]
        holder.tvPlaylistName.text = currentPlaylist.playlistTitle
        holder.cbPlaylist.isChecked = selectedPlaylists.contains(currentPlaylist.playlistID)

        holder.cbPlaylist.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedPlaylists.add(currentPlaylist.playlistID)
            } else {
                selectedPlaylists.remove(currentPlaylist.playlistID)
            }
        }
    }

    override fun getItemCount() = playlistList.size

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cbPlaylist: CheckBox = itemView.findViewById(R.id.cbPlaylist)
        val tvPlaylistName: TextView = itemView.findViewById(R.id.tvPlaylistName)
    }
}
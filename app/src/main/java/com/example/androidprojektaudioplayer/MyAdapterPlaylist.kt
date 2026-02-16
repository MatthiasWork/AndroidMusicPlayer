package com.example.androidprojektaudioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MyAdapterPlaylist(
    private val playlistList: MutableList<myPlaylist>,
    private val contextExt: Context,
    private val onPlaylistClicked: (myPlaylist) -> Unit
): RecyclerView.Adapter<MyAdapterPlaylist.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyAdapterPlaylist.MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlistcard, parent, false)
        return MyViewHolder(view, contextExt)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentPlaylist = playlistList[position]
        holder.tvTitle.text = currentPlaylist.playlistTitle
        holder.itemView.setOnClickListener {
            onPlaylistClicked(currentPlaylist)
        }
    }

    override fun getItemCount(): Int {
        return playlistList.size
    }

    class MyViewHolder(itemView: View, contextExt: Context): RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvplaylistName)
        val card: View = itemView.findViewById(R.id.playlistID)
    }
}
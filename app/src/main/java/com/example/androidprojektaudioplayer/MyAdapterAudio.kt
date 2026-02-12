package com.example.androidprojektaudioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MyAdapterAudio(private val audioList: MutableList<myAudio>,
                     private val contextExt: Context
): RecyclerView.Adapter<MyAdapterAudio.MyViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyAdapterAudio.MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.audiocard, parent, false);
        return MyViewHolder(view, contextExt);
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.itemView.isLongClickable = true;
        val currentTrack = audioList[position];
    }

    override fun getItemCount(): Int {
        return audioList.size;
    }

    class MyViewHolder(itemView: View, contextExt: Context): RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle);
        val tvGenre: TextView = itemView.findViewById(R.id.tvGenre);
        val tvArtist: TextView = itemView.findViewById(R.id.tvArtist);
        val tvDate: TextView = itemView.findViewById(R.id.tvDate);
        val card: View = itemView.findViewById(R.id.songID);
    }
}


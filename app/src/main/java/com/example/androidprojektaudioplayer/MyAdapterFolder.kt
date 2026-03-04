package com.example.androidprojektaudioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView-Adapter für die Ordnerauswahl in den Einstellungen.
 * Zeigt alle Ordner an, die Musikdateien enthalten, mit einer Checkbox zur Auswahl.
 */
class MyAdapterFolder(
    private val folderList: MutableList<String>,
    private val contextExt: Context
) : RecyclerView.Adapter<MyAdapterFolder.MyViewHolder>() {

    /** Pfade der aktuell ausgewählten Ordner. */
    val selectedFolders = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.musicfolderlib_card, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentFolder = folderList[position]

        // Nur den letzten Ordnernamen anzeigen
        holder.tvFolderName.text = currentFolder.substringAfterLast("/", currentFolder)

        // Listener entfernen, um Fehlauslösungen beim Recycling zu vermeiden
        holder.cbFolder.setOnCheckedChangeListener(null)
        holder.cbFolder.isChecked = selectedFolders.contains(currentFolder)

        holder.cbFolder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedFolders.add(currentFolder)
            else selectedFolders.remove(currentFolder)
        }
    }

    override fun getItemCount(): Int = folderList.size

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFolderName: TextView = itemView.findViewById(R.id.tvFolderName)
        val cbFolder: CheckBox = itemView.findViewById(R.id.cbFolder)
    }
}

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
 * Zeigt alle Ordner an, die Musikdateien auf dem Gerät enthalten,
 * jeweils mit einer Checkbox zur Auswahl.
 *
 * Die ausgewählten Ordner werden in [selectedFolders] gespeichert und
 * nach Bestätigung in die SharedPreferences geschrieben (über die MainActivity).
 *
 * @param folderList Die Liste aller verfügbaren Musikordner (vollständige relative Pfade)
 * @param contextExt Der Context für Ressourcenzugriff
 */
class MyAdapterFolder(
    private val folderList: MutableList<String>,
    private val contextExt: Context
) : RecyclerView.Adapter<MyAdapterFolder.MyViewHolder>() {

    /** Pfade der aktuell ausgewählten Ordner – wird von der Activity ausgelesen. */
    val selectedFolders = mutableSetOf<String>()

    /**
     * Erstellt einen neuen ViewHolder mit dem musicfolderlib_card-Layout.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.musicfolderlib_card, parent, false)
        return MyViewHolder(view)
    }

    /**
     * Bindet einen Ordnernamen an den ViewHolder.
     * Zeigt nur den letzten Teil des Pfades an (z.B. "Music" statt "Downloads/Music").
     * Setzt den Checkbox-Zustand basierend auf der aktuellen Auswahl.
     */
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentFolder = folderList[position]

        // Nur den letzten Ordnernamen anzeigen (nach dem letzten "/")
        holder.tvFolderName.text = currentFolder.substringAfterLast("/", currentFolder)

        // WICHTIG: Listener vor dem Setzen des Checked-Zustands entfernen,
        // um Fehlauslösungen beim RecyclerView-Recycling zu vermeiden.
        // Ohne dies würde der alte Listener beim Setzen von isChecked feuern.
        holder.cbFolder.setOnCheckedChangeListener(null)
        holder.cbFolder.isChecked = selectedFolders.contains(currentFolder)

        // Neuen Listener setzen: Auswahl in selectedFolders aktualisieren
        holder.cbFolder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedFolders.add(currentFolder)
            else selectedFolders.remove(currentFolder)
        }
    }

    /** Gibt die Anzahl der verfügbaren Ordner zurück. */
    override fun getItemCount(): Int = folderList.size

    /**
     * ViewHolder für einen einzelnen Ordnereintrag.
     * Enthält den Ordnernamen und eine Checkbox für die Auswahl.
     */
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFolderName: TextView = itemView.findViewById(R.id.tvFolderName)
        val cbFolder: CheckBox = itemView.findViewById(R.id.cbFolder)
    }
}

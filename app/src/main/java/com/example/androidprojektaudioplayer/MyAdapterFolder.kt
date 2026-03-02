package com.example.androidprojektaudioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.androidprojektaudioplayer.MyAdapterAudio.MyViewHolder
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.File

/**
 * RecyclerView-Adapter für die Ordnerauswahl in den Einstellungen.
 * Zeigt alle Ordner an, die Musikdateien enthalten, mit einer Checkbox
 * zur Auswahl/Abwahl. Nur Songs aus ausgewählten Ordnern werden in der App angezeigt.
 *
 * @param folderList  Die Liste aller verfügbaren Ordnerpfade mit Musikdateien
 * @param contextExt  Der Context für Layout-Inflation
 */
class MyAdapterFolder(
    private val folderList: MutableList<String>,
    private val contextExt: Context
) : RecyclerView.Adapter<MyAdapterFolder.MyViewHolder>() {

    /**
     * Set, das die Pfade der aktuell ausgewählten Ordner speichert.
     * Wird von der MainActivity gelesen, um die Auswahl in SharedPreferences zu speichern.
     */
    val selectedFolders = mutableSetOf<String>()

    /**
     * Erstellt einen neuen ViewHolder, indem das musicfolderlib_card-Layout inflated wird.
     *
     * @param parent   Die übergeordnete ViewGroup (der RecyclerView)
     * @param viewType Der View-Typ (hier nicht differenziert)
     * @return Ein neuer MyViewHolder mit dem inflateten Layout
     */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MyAdapterFolder.MyViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.musicfolderlib_card, parent, false)
        return MyViewHolder(view, contextExt)
    }

    /**
     * Bindet einen Ordner an den ViewHolder an der gegebenen Position.
     * Zeigt nur den Ordnernamen (nicht den vollen Pfad) an und setzt die Checkbox
     * basierend auf der aktuellen Auswahl. Beim Ändern der Checkbox wird der Ordner
     * zum selectedFolders-Set hinzugefügt oder daraus entfernt.
     *
     * Wichtig: Der Listener wird vor dem Setzen des Checkbox-Zustands entfernt und
     * danach neu gesetzt, um ungewollte Callbacks beim Recycling von ViewHoldern zu vermeiden.
     *
     * @param holder   Der ViewHolder, an den die Daten gebunden werden
     * @param position Die Position des Ordners in der Liste
     */
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentFolder = folderList[position]

        // Nur den Ordnernamen anzeigen, nicht den kompletten Dateipfad
        val folderName = File(currentFolder).name
        holder.tvFolderName.text = folderName

        // Listener temporär entfernen, um Fehlauslösungen beim Recycling zu vermeiden
        holder.cbFolder.setOnCheckedChangeListener(null)

        // Checkbox-Zustand basierend auf der aktuellen Auswahl setzen
        holder.cbFolder.isChecked = selectedFolders.contains(currentFolder)

        // Neuen Listener setzen: Ordner bei Auswahl/Abwahl zum Set hinzufügen/entfernen
        holder.cbFolder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedFolders.add(currentFolder)
            } else {
                selectedFolders.remove(currentFolder)
            }
        }
    }

    /**
     * Gibt die Anzahl der verfügbaren Ordner zurück.
     *
     * @return Die Anzahl der Ordner in der Liste
     */
    override fun getItemCount(): Int {
        return folderList.size
    }

    /**
     * ViewHolder-Klasse, die die UI-Elemente einer einzelnen Ordner-Card hält.
     * Enthält ein TextView für den Ordnernamen und eine CheckBox für die Auswahl.
     *
     * @param itemView   Das inflated View der musicfolderlib_card
     * @param contextExt Der Context (wird für eventuelle Erweiterungen bereitgehalten)
     */
    class MyViewHolder(itemView: View, contextExt: Context) : RecyclerView.ViewHolder(itemView) {
        val tvFolderName: TextView = itemView.findViewById(R.id.tvFolderName)
        val cbFolder: CheckBox = itemView.findViewById(R.id.cbFolder)
    }
}

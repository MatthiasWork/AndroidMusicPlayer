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

class MyAdapterFolder(
    private val folderList: MutableList<String>,
    private val contextExt: Context
) : RecyclerView.Adapter<MyAdapterFolder.MyViewHolder>() {

    // Speichert welche Ordner ausgewählt sind
    val selectedFolders = mutableSetOf<String>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MyAdapterFolder.MyViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.musicfolderlib_card, parent, false)
        return MyViewHolder(view, contextExt)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentFolder = folderList[position]

        // Nur Ordnername anzeigen, nicht kompletten Pfad
        val folderName = File(currentFolder).name
        holder.tvFolderName.text = folderName

        // Listener erst entfernen
        holder.cbFolder.setOnCheckedChangeListener(null)

        // Dann Checkbox setzen
        holder.cbFolder.isChecked = selectedFolders.contains(currentFolder)

        // Dann Listener setzen
        holder.cbFolder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedFolders.add(currentFolder)
            } else {
                selectedFolders.remove(currentFolder)
            }
        }
    }

    override fun getItemCount(): Int {
        return folderList.size
    }

    class MyViewHolder(itemView: View, contextExt: Context) : RecyclerView.ViewHolder(itemView) {
        val tvFolderName: TextView = itemView.findViewById(R.id.tvFolderName)
        val cbFolder: CheckBox = itemView.findViewById(R.id.cbFolder)
    }
}
package com.example.androidprojektaudioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MyAdapterPlaylist(
    private val playlistList: MutableList<myPlaylist>,
    private val contextExt: Context,
    private val onPlaylistClicked: (myPlaylist) -> Unit,
    private val onPlaylistEdited: (myPlaylist, String) -> Unit,
    private val onPlaylistDeleted: (myPlaylist) -> Unit
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

        holder.itemView.setOnLongClickListener {
            val inflater = LayoutInflater.from(contextExt)
            val popupView = inflater.inflate(R.layout.popupwindow_longplaylist, null)

            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            popupView.findViewById<MaterialButton>(R.id.btnEditPopUp).setOnClickListener {
                popupWindow.dismiss();
                val editView = inflater.inflate(R.layout.popupwindow_editfollowup, null)
                val editPopup = PopupWindow(
                    editView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
                )

                editView.findViewById<TextInputEditText>(R.id.etNewPlaylistName)
                    .setText(currentPlaylist.playlistTitle)

                editView.findViewById<MaterialButton>(R.id.btnSaveEditPopUp).setOnClickListener {
                    val newName = editView.findViewById<TextInputEditText>(R.id.etNewPlaylistName)
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

            popupView.findViewById<MaterialButton>(R.id.btnDeletePopUp).setOnClickListener {
                onPlaylistDeleted(currentPlaylist);
                popupWindow.dismiss();
            }

            popupWindow.showAsDropDown(holder.itemView)
            true
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
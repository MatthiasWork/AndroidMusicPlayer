package com.example.androidprojektaudioplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * RecyclerView-Adapter für die Anzeige von Audiotiteln in der Song-Liste.
 * Jeder Eintrag zeigt Titel, Künstler, Genre und Datum an.
 *
 * Über ein Kontextmenü (PopupWindow) können Songs:
 * - bearbeitet werden (Titel, Künstler, Genre, Datum)
 * - zu Playlists hinzugefügt werden
 * - aus der aktuellen Playlist entfernt werden (nur bei benutzerdefinierten Playlists)
 *
 * Die eigentliche Logik liegt nicht im Adapter, sondern wird über Callbacks
 * an die MainActivity delegiert (Adapter-Pattern / Separation of Concerns).
 *
 * @param audioList            Die anzuzeigende Liste der Audiotitel
 * @param contextExt           Der Context für LayoutInflater und Ressourcenzugriff
 * @param currentPlaylistID    Die ID der aktuell angezeigten Playlist (1 = "Alle")
 * @param onTrackClicked       Callback: Song wurde angeklickt (Wiedergabe starten)
 * @param onTrackEdited        Callback: Song-Metadaten wurden bearbeitet
 * @param onAddToPlaylist      Callback: Song soll zu einer Playlist hinzugefügt werden
 * @param onRemoveFromPlaylist Callback: Song soll aus der aktuellen Playlist entfernt werden
 */
class MyAdapterAudio(
    private val audioList: MutableList<myAudio>,
    private val contextExt: Context,
    private val currentPlaylistID: Int,
    private val onTrackClicked: (myAudio) -> Unit,
    private val onTrackEdited: (myAudio) -> Unit,
    private val onAddToPlaylist: (myAudio) -> Unit,
    private val onRemoveFromPlaylist: (myAudio) -> Unit
) : RecyclerView.Adapter<MyAdapterAudio.MyViewHolder>() {

    /**
     * Erstellt einen neuen ViewHolder mit dem audiocard-Layout.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.audiocard, parent, false)
        return MyViewHolder(view)
    }

    /**
     * Bindet die Daten eines Audiotitels an den ViewHolder.
     * Setzt die Textfelder und registriert Click-Listener für Wiedergabe und Kontextmenü.
     */
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentTrack = audioList[position]

        // Textfelder mit den Metadaten des Tracks befüllen
        holder.tvTitle.text = currentTrack.audioTitle
        holder.tvGenre.text = currentTrack.audioGenre
        holder.tvArtist.text = currentTrack.audioArtist
        holder.tvDate.text = currentTrack.audioRelDate

        // Klick auf die gesamte Card: Song abspielen über Callback
        holder.itemView.setOnClickListener { onTrackClicked(currentTrack) }

        // Klick auf den "Mehr"-Button (drei Punkte): Kontextmenü als PopupWindow anzeigen
        holder.btnMore.setOnClickListener {
            val inflater = LayoutInflater.from(contextExt)
            val popupView = inflater.inflate(R.layout.popupwindow_track, null)

            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true  // focusable = true, damit das Popup bei Touch außerhalb geschlossen wird
            )

            // "Aus Playlist entfernen" nur bei benutzerdefinierten Playlists anzeigen
            // In der Standard-Playlist "Alle" (ID 1) macht diese Option keinen Sinn
            popupView.findViewById<MaterialButton>(R.id.btnRemoveFromPlaylistPopUp).visibility =
                if (currentPlaylistID == 1) View.GONE else View.VISIBLE

            // Button: Song bearbeiten – öffnet das Edit-PopupWindow
            popupView.findViewById<MaterialButton>(R.id.btnEditAudioPopUp).setOnClickListener {
                popupWindow.dismiss()
                showEditPopup(inflater, holder, currentTrack, position)
            }

            // Button: Song zu Playlist hinzufügen – delegiert an die MainActivity
            popupView.findViewById<MaterialButton>(R.id.btnAddToPlaylistPopUp).setOnClickListener {
                onAddToPlaylist(currentTrack)
                popupWindow.dismiss()
            }

            // Button: Song aus aktueller Playlist entfernen – delegiert an die MainActivity
            popupView.findViewById<MaterialButton>(R.id.btnRemoveFromPlaylistPopUp).setOnClickListener {
                onRemoveFromPlaylist(currentTrack)
                popupWindow.dismiss()
            }

            // Popup direkt unter dem "Mehr"-Button anzeigen
            popupWindow.showAsDropDown(holder.btnMore)
        }
    }

    /**
     * Zeigt das Bearbeitungs-PopupWindow mit vorausgefüllten Feldern an.
     * Der Benutzer kann Titel, Künstler, Genre und Datum ändern.
     * Bei "Speichern" werden die Änderungen über den Callback an die Activity übergeben.
     *
     * @param inflater     Der LayoutInflater für das Popup-Layout
     * @param holder       Der ViewHolder des bearbeiteten Eintrags
     * @param currentTrack Der zu bearbeitende Track
     * @param position     Die Position in der Liste (für notifyItemChanged)
     */
    private fun showEditPopup(
        inflater: LayoutInflater,
        holder: MyViewHolder,
        currentTrack: myAudio,
        position: Int
    ) {
        val editView = inflater.inflate(R.layout.popupwindow_track_edit, null)
        val editPopup = PopupWindow(
            editView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Eingabefelder mit den aktuellen Werten vorausfüllen
        editView.findViewById<TextInputEditText>(R.id.etEditAudioTitle).setText(currentTrack.audioTitle)
        editView.findViewById<TextInputEditText>(R.id.etEditAudioArtist).setText(currentTrack.audioArtist)
        editView.findViewById<TextInputEditText>(R.id.etEditAudioGenre).setText(currentTrack.audioGenre)
        editView.findViewById<TextInputEditText>(R.id.etEditAudioDate).setText(currentTrack.audioRelDate)

        // DatePicker über die gemeinsame Hilfsmethode (DatePickerUtils) öffnen
        editView.findViewById<TextInputLayout>(R.id.tilEditAudioDate)?.setEndIconOnClickListener {
            val dateField = editView.findViewById<TextInputEditText>(R.id.etEditAudioDate)
            DatePickerUtils.showDatePicker(contextExt, dateField)
        }

        // Speichern-Button: Änderungen übernehmen und Popup schließen
        editView.findViewById<MaterialButton>(R.id.btnSaveEditAudio).setOnClickListener {
            // Track-Objekt direkt aktualisieren (Referenz in der Liste)
            currentTrack.audioTitle = editView.findViewById<TextInputEditText>(R.id.etEditAudioTitle).text.toString()
            currentTrack.audioArtist = editView.findViewById<TextInputEditText>(R.id.etEditAudioArtist).text.toString()
            currentTrack.audioGenre = editView.findViewById<TextInputEditText>(R.id.etEditAudioGenre).text.toString()
            currentTrack.audioRelDate = editView.findViewById<TextInputEditText>(R.id.etEditAudioDate).text.toString()

            // Callback an die Activity für die Datenbank-Aktualisierung
            onTrackEdited(currentTrack)

            // RecyclerView-Eintrag visuell aktualisieren
            notifyItemChanged(position)
            editPopup.dismiss()
        }

        // Popup zentriert über dem Bildschirm anzeigen
        editPopup.showAtLocation(holder.itemView, android.view.Gravity.CENTER, 0, 0)
    }

    /** Gibt die Anzahl der Einträge in der Liste zurück. */
    override fun getItemCount(): Int = audioList.size

    /**
     * ViewHolder für einen einzelnen Audioeintrag.
     * Hält Referenzen auf die Views der audiocard.xml für schnellen Zugriff.
     */
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvGenre: TextView = itemView.findViewById(R.id.tvGenre)
        val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val btnMore: ImageButton = itemView.findViewById(R.id.btnMore)
        val card: View = itemView.findViewById(R.id.songID)
    }
}

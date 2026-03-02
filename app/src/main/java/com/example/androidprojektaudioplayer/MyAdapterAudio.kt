package com.example.androidprojektaudioplayer

import android.app.DatePickerDialog
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
import java.util.Calendar

/**
 * RecyclerView-Adapter für die Anzeige von Audiotiteln in der Song-Liste.
 * Jeder Eintrag zeigt Titel, Künstler, Genre und Datum an.
 * Über ein Kontextmenü (drei Punkte) können Songs bearbeitet, zu Playlists
 * hinzugefügt oder aus der aktuellen Playlist entfernt werden.
 *
 * @param audioList              Die Liste der anzuzeigenden Audiotitel
 * @param contextExt             Der Context für Layout-Inflation und Dialoge
 * @param currentPlaylistID      Die ID der aktuellen Playlist (1 = "Alle")
 * @param onTrackClicked         Callback, wenn ein Song angeklickt wird (Wiedergabe starten)
 * @param onTrackEdited          Callback, wenn ein Song bearbeitet wurde (Daten speichern)
 * @param onAddToPlaylist        Callback, wenn ein Song zu einer Playlist hinzugefügt werden soll
 * @param onRemoveFromPlaylist   Callback, wenn ein Song aus der aktuellen Playlist entfernt werden soll
 */
class MyAdapterAudio(
    private val audioList: MutableList<myAudio>,
    private val contextExt: Context,
    private val currentPlaylistID: Int,
    private val onTrackClicked: (myAudio) -> Unit,
    private val onTrackEdited: (myAudio) -> Unit,
    private val onAddToPlaylist: (myAudio) -> Unit,
    private val onRemoveFromPlaylist: (myAudio) -> Unit
): RecyclerView.Adapter<MyAdapterAudio.MyViewHolder>() {

    /**
     * Erstellt einen neuen ViewHolder, indem das audiocard-Layout inflated wird.
     *
     * @param parent   Die übergeordnete ViewGroup (der RecyclerView)
     * @param viewType Der View-Typ (hier nicht differenziert)
     * @return Ein neuer MyViewHolder mit dem inflateten Layout
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyAdapterAudio.MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.audiocard, parent, false)
        return MyViewHolder(view, contextExt)
    }

    /**
     * Bindet die Daten eines Audiotitels an den ViewHolder an der gegebenen Position.
     * Setzt die Texte für Titel, Genre, Künstler und Datum.
     * Konfiguriert den Klick-Listener für die Wiedergabe und das Kontextmenü
     * mit den Optionen: Bearbeiten, Zu Playlist hinzufügen, Aus Playlist entfernen.
     *
     * @param holder   Der ViewHolder, an den die Daten gebunden werden
     * @param position Die Position des Elements in der Liste
     */
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentTrack = audioList[position]

        // Texte der Card mit den Track-Daten befüllen
        holder.tvTitle.text = currentTrack.audioTitle
        holder.tvGenre.text = currentTrack.audioGenre
        holder.tvArtist.text = currentTrack.audioArtist
        holder.tvDate.text = currentTrack.audioRelDate
        android.util.Log.d("Adapter", "Title: ${currentTrack.audioTitle}, Genre: ${currentTrack.audioGenre}")

        // Klick auf die gesamte Card: Song über den Callback abspielen
        holder.itemView.setOnClickListener {
            onTrackClicked(currentTrack);
        }

        // Klick auf den "Mehr"-Button (drei Punkte): Kontextmenü als PopupWindow anzeigen
        holder.btnMore.setOnClickListener {
            val inflater = LayoutInflater.from(contextExt);
            val popupView = inflater.inflate(R.layout.popupwindow_track, null);

            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            );

            // "Aus Playlist entfernen"-Button nur anzeigen, wenn NICHT die "Alle"-Playlist aktiv ist
            // (aus "Alle" kann man Songs nicht entfernen, da sie dort automatisch landen)
            if (currentPlaylistID == 1) {
                popupView.findViewById<MaterialButton>(R.id.btnRemoveFromPlaylistPopUp).visibility = View.GONE
            } else {
                popupView.findViewById<MaterialButton>(R.id.btnRemoveFromPlaylistPopUp).visibility = View.VISIBLE
            }

            // Listener: Song bearbeiten – öffnet ein Edit-PopupWindow mit vorausgefüllten Feldern
            popupView.findViewById<MaterialButton>(R.id.btnEditAudioPopUp).setOnClickListener {
                popupWindow.dismiss()
                val editView = inflater.inflate(R.layout.popupwindow_track_edit, null)
                val editPopup = PopupWindow(editView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)

                // Eingabefelder mit den aktuellen Track-Daten vorausfüllen
                editView.findViewById<TextInputEditText>(R.id.etEditAudioTitle).setText(currentTrack.audioTitle)
                editView.findViewById<TextInputEditText>(R.id.etEditAudioArtist).setText(currentTrack.audioArtist)
                editView.findViewById<TextInputEditText>(R.id.etEditAudioGenre).setText(currentTrack.audioGenre)
                editView.findViewById<TextInputEditText>(R.id.etEditAudioDate).setText(currentTrack.audioRelDate)

                // Listener: DatePicker für das Datumsfeld öffnen
                editView.findViewById<TextInputLayout>(R.id.tilEditAudioDate)?.setEndIconOnClickListener {
                    val calendar = Calendar.getInstance()
                    DatePickerDialog(
                        contextExt,
                        { _, year, month, day ->
                            val formattedDate =
                                String.format("%02d.%02d.%04d", day, month + 1, year)
                            editView.findViewById<TextInputEditText>(R.id.etEditAudioDate)
                                .setText(formattedDate)
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }

                // Listener: Bearbeitete Daten speichern – liest die neuen Werte aus den
                // Eingabefeldern, aktualisiert das Track-Objekt und ruft den Callback auf
                editView.findViewById<MaterialButton>(R.id.btnSaveEditAudio).setOnClickListener {
                    currentTrack.audioTitle = editView.findViewById<TextInputEditText>(R.id.etEditAudioTitle).text.toString()
                    currentTrack.audioArtist = editView.findViewById<TextInputEditText>(R.id.etEditAudioArtist).text.toString()
                    currentTrack.audioGenre = editView.findViewById<TextInputEditText>(R.id.etEditAudioGenre).text.toString()
                    currentTrack.audioRelDate = editView.findViewById<TextInputEditText>(R.id.etEditAudioDate).text.toString()
                    onTrackEdited(currentTrack)
                    notifyItemChanged(position)
                    editPopup.dismiss()
                }

                // Edit-Popup zentriert auf dem Bildschirm anzeigen
                editPopup.showAtLocation(holder.itemView, android.view.Gravity.CENTER, 0, 0)
            }

            // Listener: Song zu Playlist hinzufügen – ruft den Callback auf,
            // der in der MainActivity das Playlist-Auswahl-BottomSheet öffnet
            popupView.findViewById<MaterialButton>(R.id.btnAddToPlaylistPopUp).setOnClickListener {
                onAddToPlaylist(currentTrack)
                popupWindow.dismiss()
            }

            // Listener: Song aus der aktuellen Playlist entfernen
            popupView.findViewById<MaterialButton>(R.id.btnRemoveFromPlaylistPopUp).setOnClickListener {
                onRemoveFromPlaylist(currentTrack)
                popupWindow.dismiss()
            }

            // PopupWindow unterhalb des "Mehr"-Buttons anzeigen
            popupWindow.showAsDropDown(holder.btnMore)
        }


    }

    /**
     * Gibt die Anzahl der Elemente in der Audio-Liste zurück.
     * Wird vom RecyclerView verwendet, um zu wissen, wie viele Einträge angezeigt werden.
     *
     * @return Die Anzahl der Audiotitel in der Liste
     */
    override fun getItemCount(): Int {
        return audioList.size
    }

    /**
     * ViewHolder-Klasse, die die UI-Elemente einer einzelnen Audio-Card hält.
     * Speichert Referenzen auf die TextViews für Titel, Genre, Künstler und Datum
     * sowie den "Mehr"-Button für das Kontextmenü.
     *
     * @param itemView   Das inflated View der audiocard
     * @param contextExt Der Context (wird für eventuelle Erweiterungen bereitgehalten)
     */
    class MyViewHolder(itemView: View, contextExt: Context): RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle);
        val tvGenre: TextView = itemView.findViewById(R.id.tvGenre);
        val tvArtist: TextView = itemView.findViewById(R.id.tvArtist);
        val tvDate: TextView = itemView.findViewById(R.id.tvDate);
        val btnMore: ImageButton = itemView.findViewById(R.id.btnMore);
        val card: View = itemView.findViewById(R.id.songID)
    }
}

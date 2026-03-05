package com.example.androidprojektaudioplayer

import android.app.DatePickerDialog
import android.content.Context
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

/**
 * Hilfsobjekt (Singleton) für die Datumsauswahl.
 * Wird als Utility-Klasse verwendet, um doppelten DatePickerDialog-Code
 * in MainActivity und MyAdapterAudio zu vermeiden (DRY-Prinzip).
 */
object DatePickerUtils {

    /**
     * Zeigt einen DatePickerDialog an und schreibt das gewählte Datum
     * im Format "dd.MM.yyyy" in das übergebene Eingabefeld.
     *
     * Der Dialog wird mit dem aktuellen Datum vorinitialisiert.
     * Nach der Auswahl wird das Datum formatiert und direkt in das TextInputEditText geschrieben.
     *
     * @param context     Der Context für den Dialog (Activity oder Fragment)
     * @param targetField Das Eingabefeld, das mit dem gewählten Datum befüllt wird
     */
    fun showDatePicker(context: Context, targetField: TextInputEditText) {
        // Aktuelles Datum als Startwert für den Dialog holen
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            context,
            { _, year, month, day ->
                // month ist 0-basiert (Januar = 0), daher +1 für die korrekte Anzeige
                val formattedDate = String.format("%02d.%02d.%04d", day, month + 1, year)
                targetField.setText(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

package com.example.androidprojektaudioplayer

import android.app.DatePickerDialog
import android.content.Context
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

/**
 * Hilfsobjekt für die Datumsauswahl.
 * Eliminiert doppelten DatePickerDialog-Code in MainActivity und MyAdapterAudio.
 */
object DatePickerUtils {

    /**
     * Zeigt einen DatePickerDialog und schreibt das gewählte Datum
     * im Format "dd.MM.yyyy" in das übergebene Eingabefeld.
     *
     * @param context     Der Context für den Dialog
     * @param targetField Das Eingabefeld, das mit dem gewählten Datum befüllt wird
     */
    fun showDatePicker(context: Context, targetField: TextInputEditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val formattedDate = String.format("%02d.%02d.%04d", day, month + 1, year)
                targetField.setText(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

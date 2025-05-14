package com.example.bildschirmschonerapp.ui.main

import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.bildschirmschonerapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        val viewmodel = MainViewModel()
        setContentView(view)

        // Klickaktionen definieren

        // Power-Button Klick
        binding.buttonPower.setOnClickListener {
            Toast.makeText(this, "Power-Button gedrückt", Toast.LENGTH_SHORT).show()
            // Hier die Funktion zum Aktivieren des Bildschirm änderns einfügen

            viewmodel.setRandomWallpaper(applicationContext) // setzt das wallpaper direkt. Sollte im hintergrund laufen.
            //damit dass funktioniert müssen die permissions im settings menü gegeben werden.
            if(true) //Backgrounddienst läuft
            {
                //Dienst killen
            } else {
                //Dienst starten
            }
        }

        // Reset-Button Klick
        binding.buttonReset.setOnClickListener {
            Toast.makeText(this, "Reset-Button gedrückt", Toast.LENGTH_SHORT).show()
            resetValues()  // Alle Eingabefelder zurücksetzen
        }

        // SeekBar Change Listener
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.editInterval.setText(progress.toString()) // Intervallwert im EditText anzeigen
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // TextWatcher für Bildnummer (EditText)
        binding.editImgNumber.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // Hier könnte Logik kommen, falls du mit der Eingabe etwas machen möchtest
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.radioBtnNew.setOnClickListener { view ->
            binding.radioBtnNew.isChecked = true
            binding.radioBtnAll.isChecked = false
        }

        binding.radioBtnAll.setOnClickListener { view ->
            binding.radioBtnAll.isChecked = true
            binding.radioBtnNew.isChecked = false
        }
    }

    // Funktion zum Zurücksetzen der Werte
    private fun resetValues() {
        binding.editImgNumber.setText("") // Bildnummer zurücksetzen
        binding.editInterval.setText("13") // Intervall auf Standardwert setzen
        binding.seekBar.progress = 13 // SeekBar auf Standardwert setzen
        binding.radioBtnAll.isChecked = true // RadioButton "Alle Bilder" auswählen
        binding.radioBtnNew.isChecked = false
        binding.intervalUnitSpinner.setSelection(0) // Intervall-Einheit auf den ersten Wert setzen
    }

    // Funktion, um die aktuellen Einstellungen zu holen
    private fun getUserInput() {
        val imgNumberStr = binding.editImgNumber.text.toString()
        if (imgNumberStr.isNotEmpty()) {
            val imgNumber = imgNumberStr.toInt()
            // Verarbeite imgNumber, z.B. sende es ans Backend
        } else {
            Toast.makeText(this, "Bitte eine Zahl für die Bildanzahl eingeben", Toast.LENGTH_SHORT).show()
        }

        val intervalValue = binding.seekBar.progress
        val intervalUnit = binding.intervalUnitSpinner.selectedItem.toString()
        // Verarbeite intervalValue und intervalUnit
    }
}

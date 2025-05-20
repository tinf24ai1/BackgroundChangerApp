package com.example.bildschirmschonerapp.ui.main

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.bildschirmschonerapp.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import kotlinx.coroutines.launch
//import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.guava.await

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Klickaktionen definieren

        // Power-Button Klick
        binding.buttonPower.setOnClickListener {
            //Toast.makeText(this, "Power-Button gedrückt", Toast.LENGTH_SHORT).show()

                lifecycleScope.launch {
                    try {
                    val workManager = WorkManager.getInstance(this@MainActivity)
                    val workInfos = workManager.getWorkInfosForUniqueWork("MyBackgroundWork").await()
                    val isRunning = workInfos.any {
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                    }

                    if (isRunning) {
                        workManager.cancelUniqueWork("MyBackgroundWork")
                        Toast.makeText(this@MainActivity, "Dienst gestoppt", Toast.LENGTH_SHORT).show()
                    } else {
                        val workRequest = PeriodicWorkRequestBuilder<BackgroundWorker>(15, TimeUnit.MINUTES)
                            .build()

                        workManager.enqueueUniquePeriodicWork(
                            "MyBackgroundWork",
                            ExistingPeriodicWorkPolicy.KEEP,
                            workRequest
                        )
                        Toast.makeText(this@MainActivity, "Dienst gestartet", Toast.LENGTH_SHORT).show()
                    }
                }
                    catch (e: Exception){
                        Toast.makeText(this@MainActivity, e.message.toString(), Toast.LENGTH_SHORT).show()
                    }
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
    }

    // Funktion zum Zurücksetzen der Werte
    private fun resetValues() {
        binding.editImgNumber.setText("") // Bildnummer zurücksetzen
        binding.editInterval.setText("13") // Intervall auf Standardwert setzen
        binding.seekBar.progress = 13 // SeekBar auf Standardwert setzen
        binding.radioBtnAll.isChecked = true // RadioButton "Alle Bilder" auswählen
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

class BackgroundWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun doWork(): Result {
        return try {
            Log.d("MyWorker", "Tick at ${System.currentTimeMillis()}")
            val vm = MainViewModel();
            vm.setRandomWallpaper(applicationContext)
            //TODO Hier die Hintergrundlogik einfügen (Background ändern)

            Result.success()
        } catch (e: Exception) {
            Log.e("MyWorker", "Fehler im Hintergrunddienst", e)
            Result.retry()
        }
    }
}

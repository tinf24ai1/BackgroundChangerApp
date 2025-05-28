package com.example.bildschirmschonerapp.ui.main

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var mainViewModel: MainViewModel // Deklariere das ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        mainViewModel = MainViewModel.getInstance(application) // Hier die Singleton-Instanz abrufen
        // Klickaktionen definieren

        // Power-Button Klick
        binding.buttonPower.setOnClickListener {
            val intervalText = binding.editInterval.text.toString()
            val interval = intervalText.toIntOrNull() ?: 15 // Fallback, falls leer oder ungültig

            val selectedUnit = binding.intervalUnitSpinner.selectedItem.toString()
            val timeUnit = when (selectedUnit.lowercase()) {
                "stunden" -> TimeUnit.HOURS
                "minuten" -> TimeUnit.MINUTES
                else -> TimeUnit.MINUTES // Fallback
            }

            // Minimum-Check: PeriodicWorkRequest unterstützt nur 15+ Minuten!
            if (timeUnit == TimeUnit.MINUTES && interval < 15) {
                Toast.makeText(this, "Mindestintervall: 15 Minuten", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val workManager = WorkManager.getInstance(this@MainActivity)
                    val workInfos =
                        workManager.getWorkInfosForUniqueWork("MyBackgroundWork").await()
                    val isRunning = workInfos.any {
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                    }

                    if (isRunning) {
                        workManager.cancelUniqueWork("MyBackgroundWork")
                        Toast.makeText(this@MainActivity, "Dienst gestoppt", Toast.LENGTH_SHORT).show()
                    } else {
                        val workRequest = PeriodicWorkRequestBuilder<BackgroundWorker>(
                            interval.toLong(), timeUnit
                        ).build()

                        workManager.enqueueUniquePeriodicWork(
                            "MyBackgroundWork",
                            ExistingPeriodicWorkPolicy.KEEP,
                            workRequest
                        )
                        Toast.makeText(this@MainActivity, "Dienst gestartet", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, e.message.toString(), Toast.LENGTH_SHORT)
                        .show()
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
                getUserInput()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.radioBtnNew.setOnClickListener { view ->
            binding.radioBtnNew.isChecked = true
            binding.radioBtnAll.isChecked = false
            mainViewModel.UseImgNumber = true
        }

        binding.radioBtnAll.setOnClickListener { view ->
            binding.radioBtnAll.isChecked = true
            binding.radioBtnNew.isChecked = false
            mainViewModel.UseImgNumber = false
        }
    }

    // Funktion zum Zurücksetzen der Werte
    private fun resetValues() {
        binding.editImgNumber.setText("") // Bildnummer zurücksetzen
        binding.editInterval.setText("13") // Intervall auf Standardwert setzen
        binding.seekBar.progress = 13 // SeekBar auf Standardwert setzen
        binding.radioBtnAll.isChecked = true // RadioButton "Alle Bilder" auswählen
        binding.radioBtnNew.isChecked = false
        mainViewModel.UseImgNumber = false
        binding.intervalUnitSpinner.setSelection(0) // Intervall-Einheit auf den ersten Wert setzen
    }

    // Funktion, um die aktuellen Einstellungen zu holen
    private fun getUserInput() {
        val imgNumberStr = binding.editImgNumber.text.toString()
        if (imgNumberStr.isNotEmpty()) {
            val imgNumber = imgNumberStr.toInt()
            mainViewModel.ImgNumber = imgNumber
        } else {
            Toast.makeText(this, "Bitte eine Zahl für die Bildanzahl eingeben", Toast.LENGTH_SHORT).show()
        }

        val intervalValue = binding.seekBar.progress
        val intervalUnit = binding.intervalUnitSpinner.selectedItem.toString()
        // Verarbeite intervalValue und intervalUnit
    }

    override fun onResume() {
        super.onResume()

        // Post the dialog to run after the activity is ready
        Handler(Looper.getMainLooper()).post {
            checkPermissions(this@MainActivity)
        }
    }

    private fun checkPermissions(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            android.app.AlertDialog.Builder(context)
                .setTitle("Berechtigung erforderlich")
                .setMessage("Bitte aktiviere die erforderlichen Foto und Video Berechtigungen in den App-Einstellungen.")
                .setPositiveButton("Zu den Einstellungen") { _, _ ->
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }
}

class BackgroundWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun doWork(): Result {
        return try {
            Log.d("MyWorker", "Tick at ${System.currentTimeMillis()}")
            val vm = MainViewModel.getInstance(applicationContext as Application) // Hier die Singleton-Instanz abrufen
            vm.setRandomWallpaper(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Log.e("MyWorker", "Fehler im Hintergrunddienst", e)
            Result.retry()
        }
    }
}

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
import androidx.appcompat.app.AppCompatDelegate
import com.example.bildschirmschonerapp.R
import kotlinx.coroutines.launch
//import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.guava.await
import android.view.inputmethod.EditorInfo
import android.graphics.Rect
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mainViewModel: MainViewModel // Deklariere das ViewModel
    private var minSeekValue = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

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

                        Toast.makeText(this@MainActivity, "Dienst gestoppt", Toast.LENGTH_SHORT)
                            .show()
                        binding.buttonPower.setColorFilter(
                            ContextCompat.getColor(this@MainActivity, R.color.power_inactive)
                        )
                    } else {
                        val workRequest = PeriodicWorkRequestBuilder<BackgroundWorker>(
                            interval.toLong(), timeUnit
                        ).build()

                        workManager.enqueueUniquePeriodicWork(
                            "MyBackgroundWork",
                            ExistingPeriodicWorkPolicy.KEEP,
                            workRequest
                        )
                        Toast.makeText(this@MainActivity, "Dienst gestartet", Toast.LENGTH_SHORT)
                            .show()
                        binding.buttonPower.setColorFilter(
                            ContextCompat.getColor(this@MainActivity, R.color.power_active)
                        )
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

        binding.editInterval.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val selectedUnit = binding.intervalUnitSpinner.selectedItem.toString().lowercase()
                val (min, max) = when (selectedUnit) {
                    "minuten" -> 15 to 60
                    "stunden" -> 1 to 24
                    else -> 1 to 24
                }
                validateIntervalInput(min, max)
                true
            } else {
                false
            }
        }

        binding.intervalUnitSpinner.setOnItemSelectedListener(object :
            android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val selectedUnit = parent.getItemAtPosition(position).toString()
                when (selectedUnit.lowercase()) {
                    "minuten" -> {
                        updateSeekBarRange(15, 60)
                    }

                    "stunden" -> {
                        updateSeekBarRange(1, 24)
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
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
        binding.editInterval.setText("12") // Intervall auf Standardwert setzen
        binding.seekBar.progress = 12 // SeekBar auf Standardwert setzen
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
            Toast.makeText(this, "Bitte eine Zahl für die Bildanzahl eingeben", Toast.LENGTH_SHORT)
                .show()
        }

        val intervalValue = binding.seekBar.progress
        val intervalUnit = binding.intervalUnitSpinner.selectedItem.toString()
        // Verarbeite intervalValue und intervalUnit
    }

    private fun updateSeekBarRange(min: Int, max: Int) {
        val currentProgress = binding.seekBar.progress
        val newProgress = currentProgress.coerceIn(min, max)


        binding.seekBar.max = max
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            binding.seekBar.min = min
        }

        binding.seekBar.progress = newProgress
        binding.editInterval.setText(newProgress.toString())

        // Optional: EditText auf Änderungen beschränken
        binding.editInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validateIntervalInput(min, max)
            }
        }
        binding.editInterval.filters = arrayOf(android.text.InputFilter.LengthFilter(2))
    }

    private fun validateIntervalInput(min: Int, max: Int) {
        val inputStr = binding.editInterval.text.toString()
        val input = inputStr.toIntOrNull()

        if (input != null) {
            val validated = input.coerceIn(min, max)
            if (validated != input) {
                binding.editInterval.setText(validated.toString())
            }
            binding.seekBar.progress = validated
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (currentFocus != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val view = currentFocus
            val rect = Rect()
            view?.getGlobalVisibleRect(rect)
            if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                view?.clearFocus()
                imm.hideSoftInputFromWindow(view?.windowToken, 0)

                // Optional: validiere die Eingabe bei Fokusverlust
                val selectedUnit = binding.intervalUnitSpinner.selectedItem.toString().lowercase()
                val (min, max) = when (selectedUnit) {
                    "minuten" -> 15 to 60
                    "stunden" -> 1 to 24
                    else -> 1 to 24
                }
                validateIntervalInput(min, max)
            }
        }
        return super.dispatchTouchEvent(ev)
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
                    val intent =
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .apply {
                                data =
                                    android.net.Uri.fromParts("package", context.packageName, null)
                            }
                    context.startActivity(intent)
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }
}

class BackgroundWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun doWork(): Result {
        return try {
            Log.d("MyWorker", "Tick at ${System.currentTimeMillis()}")
            val vm =
                MainViewModel.getInstance(applicationContext as Application) // Hier die Singleton-Instanz abrufen
            vm.setRandomWallpaper(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Log.e("MyWorker", "Fehler im Hintergrunddienst", e)
            Result.retry()
        }
    }
}

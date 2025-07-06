package com.example.bildschirmschonerapp.ui.main

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.bildschirmschonerapp.R
import com.example.bildschirmschonerapp.databinding.ActivityMainBinding
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mainViewModel: MainViewModel
    private var statusUpdateHandler: Handler? = null
    private var statusUpdateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        mainViewModel = MainViewModel.getInstance(application)
        binding.editImgNumber.setText("50")

        setupClickListeners()
    }

    private fun setupClickListeners() {
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
                        // Create constraints to make WorkManager more reliable
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                            .setRequiresBatteryNotLow(false)
                            .setRequiresCharging(false)
                            .setRequiresDeviceIdle(false)
                            .setRequiresStorageNotLow(false)
                            .build()

                        val workRequest = PeriodicWorkRequestBuilder<BackgroundWorker>(
                            interval.toLong(), timeUnit
                        ).setConstraints(constraints).build()

                        workManager.enqueueUniquePeriodicWork(
                            "MyBackgroundWork",
                            ExistingPeriodicWorkPolicy.UPDATE, // Use UPDATE instead of deprecated REPLACE
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

        binding.buttonReset.setOnClickListener {
            Toast.makeText(this, "Reset-Button gedrückt", Toast.LENGTH_SHORT).show()
            resetValues()
        }
        
        binding.buttonChangeNow.setOnClickListener {
            changeWallpaperNow()
        }

        setupSeekBarAndSpinner()
        setupRadioButtons()
        setupMiuiCheckbox()
        startStatusUpdates()
    }

    private fun setupSeekBarAndSpinner() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.editInterval.setText(progress.toString())
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

        binding.editImgNumber.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                getUserInput()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupRadioButtons() {
        binding.radioBtnNew.setOnClickListener { _ ->
            binding.radioBtnNew.isChecked = true
            binding.radioBtnAll.isChecked = false
            binding.radioBtnBackgrounds.isChecked = false
            mainViewModel.UseImgNumber = true
            mainViewModel.UseBackgroundsFolder = false
            mainViewModel.resetCycle()
            updateCycleStatus()
        }

        binding.radioBtnAll.setOnClickListener { _ ->
            binding.radioBtnAll.isChecked = true
            binding.radioBtnNew.isChecked = false
            binding.radioBtnBackgrounds.isChecked = false
            mainViewModel.UseImgNumber = false
            mainViewModel.UseBackgroundsFolder = false
            mainViewModel.resetCycle()
            updateCycleStatus()
        }

        binding.radioBtnBackgrounds.setOnClickListener { _ ->
            binding.radioBtnBackgrounds.isChecked = true
            binding.radioBtnAll.isChecked = false
            binding.radioBtnNew.isChecked = false
            mainViewModel.UseImgNumber = false
            mainViewModel.UseBackgroundsFolder = true
            mainViewModel.resetCycle()
            updateCycleStatus()
        }
    }

    private fun setupMiuiCheckbox() {
        binding.checkBoxMiui.setOnCheckedChangeListener { _, isChecked ->
            mainViewModel.preventMiuiThemeChange = isChecked
        }
    }

    private fun resetValues() {
        binding.editImgNumber.setText("50")
        binding.editInterval.setText("12")
        binding.seekBar.progress = 12
        binding.radioBtnAll.isChecked = true
        binding.radioBtnNew.isChecked = false
        binding.radioBtnBackgrounds.isChecked = false
        binding.checkBoxMiui.isChecked = true
        mainViewModel.UseImgNumber = false
        mainViewModel.UseBackgroundsFolder = false
        mainViewModel.preventMiuiThemeChange = true
        mainViewModel.resetCycle()
        binding.intervalUnitSpinner.setSelection(0)
        updateCycleStatus()
        Toast.makeText(this, "Einstellungen zurückgesetzt", Toast.LENGTH_SHORT).show()
    }

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
        
        mainViewModel.intervalValue = intervalValue
        mainViewModel.intervalUnit = intervalUnit
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
            checkBatteryOptimization(this@MainActivity)
        }
    }

    private fun checkPermissions(context: Context) {
        if (when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_MEDIA_IMAGES
                    ) != PackageManager.PERMISSION_GRANTED
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                }
                else -> {
                    true
                }
        }) {
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

    private fun checkBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                android.app.AlertDialog.Builder(context)
                    .setTitle("Batterieoptimierung deaktivieren")
                    .setMessage("Für eine zuverlässige Funktion der App sollte die Batterieoptimierung deaktiviert werden. Dies verhindert, dass Android die App nach 30 Minuten stoppt.")
                    .setPositiveButton("Zu den Einstellungen") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback zur allgemeinen Batterieeinstellung
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Später", null)
                    .show()
            }
        }
    }

    private fun changeWallpaperNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lifecycleScope.launch {
                try {
                    binding.buttonChangeNow.isEnabled = false
                    binding.buttonChangeNow.text = "Wird geändert..."
                    
                    val success = mainViewModel.setManualWallpaper(this@MainActivity)
                    
                    if (success) {
                        Toast.makeText(this@MainActivity, "Hintergrundbild erfolgreich geändert!", Toast.LENGTH_SHORT).show()
                        updateCycleStatus()
                    } else {
                        Toast.makeText(this@MainActivity, "Fehler beim Ändern des Hintergrundbilds", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "Error changing wallpaper manually", e)
                } finally {
                    binding.buttonChangeNow.isEnabled = true
                    binding.buttonChangeNow.text = "Jetzt ändern"
                }
            }
        } else {
            Toast.makeText(this, "Diese Funktion erfordert Android 13+", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateCycleStatus() {
        val status = mainViewModel.getCycleStatus()
        binding.textCycleStatus.text = "Durchlauf: $status"
    }
    
    private fun startStatusUpdates() {
        statusUpdateHandler = Handler(Looper.getMainLooper())
        statusUpdateRunnable = object : Runnable {
            override fun run() {
                updateCycleStatus()
                statusUpdateHandler?.postDelayed(this, 5000) // Update every 5 seconds
            }
        }
        statusUpdateHandler?.post(statusUpdateRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        statusUpdateHandler?.removeCallbacks(statusUpdateRunnable!!)
    }

}

class BackgroundWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun doWork(): Result {
        return try {
            Log.d("MyWorker", "Tick at ${System.currentTimeMillis()}")
            val vm = MainViewModel.getInstance(applicationContext as Application)
            
            // Check if the ViewModel state is still valid
            Log.d("MyWorker", "UseImgNumber: ${vm.UseImgNumber}, UseBackgroundsFolder: ${vm.UseBackgroundsFolder}")
            
            val success = vm.setRandomWallpaper(applicationContext)
            
            if (success) {
                Log.d("MyWorker", "Wallpaper changed successfully")
                Result.success()
            } else {
                Log.w("MyWorker", "Failed to change wallpaper, retrying...")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("MyWorker", "Fehler im Hintergrunddienst", e)
            // Don't give up immediately, try again
            Result.retry()
        }
    }
}

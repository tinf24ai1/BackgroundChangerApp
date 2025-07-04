package com.example.bildschirmschonerapp.ui.main

import android.Manifest
import android.app.Application
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import java.io.IOException
import java.util.Random
import kotlin.math.min

class MainViewModel(application: Application) : AndroidViewModel(application) {
    public var ImgNumber = 50
    public var UseImgNumber = false
    public var UseBackgroundsFolder = false
    public var UseCycleMode = false
    public var intervalValue = 12
    public var intervalUnit = "Stunden"
    public var preventMiuiThemeChange = true
    
    // Variables for cycle mode
    private var currentImageIndex = 0
    private var imageUrisList: List<Uri> = emptyList()
    private var cycleInitialized = false

    companion object {
        @Volatile
        private var INSTANCE: MainViewModel? = null

        fun getInstance(application: Application): MainViewModel {
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE ?: MainViewModel(application).also { INSTANCE = it }
                instance
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun setRandomWallpaper(context: Context): Boolean {
        // Check for necessary permissions. Currently the app does not ask for permissions at any point so those need to be granted via settings.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.SET_WALLPAPER) != PackageManager.PERMISSION_GRANTED) {
            println("Permissions not granted!")
            return false
        }

        val imageUris = if (UseBackgroundsFolder) {
            getImageUrisFromBackgroundsFolder(context)
        } else {
            getAllImageUris(context)
        }

        if (imageUris.isEmpty()) {
            println("No images found in the gallery.")
            return false
        }

        val selectedImageUri = if (UseCycleMode) {
            // Cycle mode: go through all images sequentially
            if (!cycleInitialized || imageUrisList != imageUris) {
                // Initialize or refresh the image list
                imageUrisList = if (!UseImgNumber || ImgNumber <= 0) {
                    imageUris
                } else {
                    imageUris.take(min(imageUris.size, ImgNumber))
                }
                currentImageIndex = 0
                cycleInitialized = true
                println("Cycle mode initialized with ${imageUrisList.size} images")
            }
            
            val selectedUri = imageUrisList[currentImageIndex]
            println("Cycle mode: showing image ${currentImageIndex + 1} of ${imageUrisList.size}")
            
            // Move to next image, reset to 0 if we've reached the end
            currentImageIndex = (currentImageIndex + 1) % imageUrisList.size
            if (currentImageIndex == 0) {
                println("Cycle completed, starting over")
            }
            
            selectedUri
        } else {
            // Random mode: select a random image
            val randomIndex = if (!UseImgNumber || ImgNumber <= 0) {
                Random().nextInt(imageUris.size)
            } else {
                Random().nextInt(min(imageUris.size, ImgNumber))
            }
            println("Random mode: selected image at index $randomIndex")
            imageUris[randomIndex]
        }

        val wallpaperManager = WallpaperManager.getInstance(context)

        try {
            context.contentResolver.openInputStream(selectedImageUri)?.use { inputStream ->
                if (preventMiuiThemeChange && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        // Für MIUI: Verhindere automatische Theme-Änderungen
                        // Setze nur das Hintergrundbild für den Homescreen, nicht das Lockscreen
                        wallpaperManager.setStream(inputStream, null, true, WallpaperManager.FLAG_SYSTEM)
                    } catch (e: Exception) {
                        // Fallback zur normalen Methode
                        println("MIUI-spezifische Methode fehlgeschlagen, verwende Standard-Methode: ${e.message}")
                        wallpaperManager.setStream(inputStream)
                    }
                } else {
                    wallpaperManager.setStream(inputStream)
                }
            }
            println("Wallpaper set successfully!")
            return true
        } catch (e: IOException) {
            println("Error setting wallpaper: ${e.localizedMessage}")
            return false
        }


    }

    private fun getAllImageUris(context: Context): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                imageUris.add(imageUri)
            }
        }
        return imageUris
    }

    private fun getImageUrisFromBackgroundsFolder(context: Context): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("Hintergründe")
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                imageUris.add(imageUri)
            }
        }
        return imageUris
    }

    // Function to reset the cycle when settings change
    fun resetCycle() {
        currentImageIndex = 0
        cycleInitialized = false
        imageUrisList = emptyList()
        println("Cycle reset")
    }
}
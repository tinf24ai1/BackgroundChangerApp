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
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import java.io.IOException
import java.util.Random
import kotlin.math.min

class MainViewModel(application: Application) : AndroidViewModel(application) {
    public var ImgNumber = 50
    public var UseImgNumber = false
    public var UseBackgroundsFolder = false
    public var intervalValue = 12
    public var intervalUnit = "Stunden"
    public var preventMiuiThemeChange = true
    
    // Cycle tracking variables
    private var currentImageSet: List<Uri> = emptyList()
    private var usedImageIndices = mutableSetOf<Int>()
    private var lastImageSetHash = 0

    companion object {
        private const val TAG = "MainViewModel"
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
        return setWallpaper(context, false)
    }
    
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun setManualWallpaper(context: Context): Boolean {
        return setWallpaper(context, true)
    }
    
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setWallpaper(context: Context, isManual: Boolean): Boolean {
        // Check for necessary permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.SET_WALLPAPER) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permissions not granted!")
            return false
        }

        val imageUris = if (UseBackgroundsFolder) {
            getImageUrisFromBackgroundsFolder(context)
        } else {
            getAllImageUris(context)
        }

        if (imageUris.isEmpty()) {
            Log.w(TAG, "No images found in the gallery.")
            return false
        }

        // Check if image set changed
        val currentHash = imageUris.hashCode()
        if (currentHash != lastImageSetHash) {
            Log.d(TAG, "Image set changed, resetting cycle")
            currentImageSet = imageUris
            usedImageIndices.clear()
            lastImageSetHash = currentHash
        }

        // Apply image number limit if needed
        val availableImages = if (UseImgNumber && ImgNumber > 0) {
            imageUris.take(ImgNumber)
        } else {
            imageUris
        }

        val selectedIndex = getNextImageIndex(availableImages, isManual)
        if (selectedIndex == -1) {
            Log.w(TAG, "No valid image index found")
            return false
        }

        Log.d(TAG, "Selected image at index $selectedIndex (${usedImageIndices.size}/${availableImages.size} used)")
        val selectedImageUri = availableImages[selectedIndex]
        usedImageIndices.add(selectedIndex)

        return setWallpaperFromUri(context, selectedImageUri)
    }

    private fun getAllImageUris(context: Context): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.SIZE} > ? AND ${MediaStore.Images.Media.MIME_TYPE} IN (?, ?, ?, ?)"
        val selectionArgs = arrayOf("10240", "image/jpeg", "image/png", "image/webp", "image/heic") // Only images larger than 10KB and common formats
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    imageUris.add(imageUri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying images", e)
        }
        
        Log.d(TAG, "Found ${imageUris.size} images")
        return imageUris
    }

    private fun getImageUrisFromBackgroundsFolder(context: Context): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.SIZE} > ? AND ${MediaStore.Images.Media.MIME_TYPE} IN (?, ?, ?, ?)"
        val selectionArgs = arrayOf("Hintergründe", "10240", "image/jpeg", "image/png", "image/webp", "image/heic") // Only images larger than 10KB and common formats
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    imageUris.add(imageUri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying background images", e)
        }
        
        Log.d(TAG, "Found ${imageUris.size} background images")
        return imageUris
    }

    private fun getNextImageIndex(availableImages: List<Uri>, isManual: Boolean): Int {
        if (availableImages.isEmpty()) return -1
        
        // If all images have been used, reset the cycle
        if (usedImageIndices.size >= availableImages.size) {
            Log.d(TAG, "All images used, resetting cycle")
            usedImageIndices.clear()
        }
        
        // Find unused images
        val unusedIndices = (0 until availableImages.size).filter { it !in usedImageIndices }
        
        if (unusedIndices.isEmpty()) {
            // This shouldn't happen due to the reset above, but just in case
            return Random().nextInt(availableImages.size)
        }
        
        // For manual changes, we might want to show some variety
        return if (isManual && unusedIndices.size > 1) {
            // Try to avoid the most recently used image for manual changes
            val lastUsedIndex = usedImageIndices.maxOrNull()
            val filteredIndices = if (lastUsedIndex != null && unusedIndices.size > 1) {
                unusedIndices.filter { it != lastUsedIndex }
            } else {
                unusedIndices
            }
            filteredIndices.random()
        } else {
            unusedIndices.random()
        }
    }
    
    private fun setWallpaperFromUri(context: Context, imageUri: Uri): Boolean {
        val wallpaperManager = WallpaperManager.getInstance(context)

        try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                if (preventMiuiThemeChange && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        // Für MIUI: Verhindere automatische Theme-Änderungen
                        // Setze nur das Hintergrundbild für den Homescreen, nicht das Lockscreen
                        wallpaperManager.setStream(inputStream, null, true, WallpaperManager.FLAG_SYSTEM)
                    } catch (e: Exception) {
                        // Fallback zur normalen Methode
                        Log.w(TAG, "MIUI-spezifische Methode fehlgeschlagen, verwende Standard-Methode", e)
                        wallpaperManager.setStream(inputStream)
                    }
                } else {
                    wallpaperManager.setStream(inputStream)
                }
            }
            Log.d(TAG, "Wallpaper set successfully!")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error setting wallpaper", e)
            return false
        }
    }
    
    fun getCycleStatus(): String {
        val totalImages = if (UseImgNumber && ImgNumber > 0) {
            min(currentImageSet.size, ImgNumber)
        } else {
            currentImageSet.size
        }
        return "${usedImageIndices.size}/$totalImages"
    }
    
    fun resetCycle() {
        usedImageIndices.clear()
        Log.d(TAG, "Cycle manually reset")
    }
}
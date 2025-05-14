package com.example.bildschirmschonerapp.ui.main

import android.Manifest
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.SET_WALLPAPER
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Random

class MainViewModel : ViewModel() {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun setRandomWallpaper(context: Context): Boolean {
        // Check for necessary permissions. Currently the app does not ask for permissions at any point so those need to be granted via settings.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.SET_WALLPAPER) != PackageManager.PERMISSION_GRANTED) {
            println("Permissions not granted!")
            return false
        }

        val imageUris = getAllImageUris(context)

        if (imageUris.isEmpty()) {
            println("No images found in the gallery.")
            return false
        }

        val randomIndex = Random().nextInt(imageUris.size)
        println(randomIndex)
        val randomImageUri = imageUris[randomIndex]

        val wallpaperManager = WallpaperManager.getInstance(context)

        try {
            context.contentResolver.openInputStream(randomImageUri)?.use { inputStream ->
                wallpaperManager.setStream(inputStream)
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
}
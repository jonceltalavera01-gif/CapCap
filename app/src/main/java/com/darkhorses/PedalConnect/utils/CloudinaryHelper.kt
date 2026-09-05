package com.darkhorses.PedalConnect.utils

import android.content.Context
import android.util.Log
import com.cloudinary.Cloudinary
import com.cloudinary.android.MediaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CloudinaryHelper {
    private var initialized = false
    private lateinit var cloudinary: Cloudinary

    fun init(context: Context) {
        if (initialized) return
        try {
            val config = mutableMapOf<String, Any>()
            config["cloud_name"] = "iyplfpom"
            config["api_key"]    = "422199837533342"
            config["api_secret"] = "UGXS1BFgZsxvrF-29fWRO9GobCM"
            config["secure"]     = true
            
            // 1. Initialize MediaManager (the Android wrapper)
            try {
                MediaManager.init(context, config)
            } catch (e: Exception) {
                Log.w("CloudinaryHelper", "MediaManager already initialized or skipped: ${e.message}")
            }
            
            // 2. Initialize base Cloudinary (the Java core) for reliable direct uploads
            cloudinary = Cloudinary(config)
            initialized = true
            Log.d("CloudinaryHelper", "Cloudinary initialized successfully")
        } catch (e: Exception) {
            Log.e("CloudinaryHelper", "Failed to initialize Cloudinary", e)
        }
    }

    data class CloudinaryResult(val url: String, val publicId: String)

    /**
     * Uploads image bytes directly to Cloudinary.
     * Uses the base Java SDK which is more reliable for signed uploads from the client.
     */
    suspend fun uploadImage(bytes: ByteArray): CloudinaryResult = withContext(Dispatchers.IO) {
        if (!initialized) {
            // Best-effort initialization attempt if called prematurely
            throw Exception("CloudinaryHelper not initialized.")
        }
        if (bytes.isEmpty()) throw Exception("Cannot upload empty image bytes")

        try {
            Log.d("CloudinaryHelper", "Starting upload... (${bytes.size} bytes)")
            
            val params = mutableMapOf<String, Any>()
            params["resource_type"] = "image"
            
            // This is a blocking call in the Java SDK, hence withContext(Dispatchers.IO)
            val uploadResult = cloudinary.uploader().upload(bytes, params)
            
            val url = uploadResult["secure_url"] as? String ?: uploadResult["url"] as? String ?: ""
            val publicId = uploadResult["public_id"] as? String ?: ""
            
            if (url.isNotEmpty()) {
                Log.d("CloudinaryHelper", "Upload successful: $url")
                CloudinaryResult(url, publicId)
            } else {
                throw Exception("Upload succeeded but returned no URL")
            }
        } catch (e: Exception) {
            Log.e("CloudinaryHelper", "Cloudinary upload exception", e)
            // Rethrow with a cleaner message for the UI
            val message = e.message ?: "Unknown upload error"
            throw Exception(message)
        }
    }
}

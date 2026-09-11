package com.darkhorses.PedalConnect.utils

import com.darkhorses.PedalConnect.BuildConfig

/**
 * Manages fallback logic for OpenRouteService API keys.
 * If one key fails due to quota or auth errors, it provides the next available key.
 */
object OrsApiManager {
    private val apiKeys: List<String> by lazy {
        listOf(
            BuildConfig.ORS_API_KEY_1,
            BuildConfig.ORS_API_KEY_2
        ).filter { it.isNotBlank() }
    }

    private var currentKeyIndex = 0

    /**
     * Returns the current API key to use.
     */
    fun getApiKey(): String {
        val keys = apiKeys
        if (keys.isEmpty()) return ""
        return keys[currentKeyIndex % keys.size]
    }

    /**
     * Marks the current API key as failed and moves to the next one.
     * Returns true if there is another key to try, false if all keys have been exhausted for this attempt.
     */
    @Synchronized
    fun switchToNextKey(): Boolean {
        val keys = apiKeys
        if (keys.size <= 1) return false
        currentKeyIndex = (currentKeyIndex + 1) % keys.size
        // If we wrapped back to the start, we've tried them all
        return currentKeyIndex != 0 
    }
    
    /**
     * Resets to the primary key.
     */
    @Synchronized
    fun resetToPrimary() {
        currentKeyIndex = 0
    }
}

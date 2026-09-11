package com.darkhorses.PedalConnect.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object BiometricHelper {
    private const val ENCRYPTED_PREFS_NAME = "secure_pedal_connect_prefs"
    private const val KEY_BIOMETRIC_EMAIL = "biometric_email"
    private const val KEY_BIOMETRIC_PASSWORD = "biometric_password"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    }

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // This is called when fingerprint is recognized but not valid, or other failures.
                    // Usually we don't show a toast here as the system UI handles it.
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Login")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use password")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return try {
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // If creation fails (due to Keystore corruption/key mismatch), 
            // clear the corrupted file and try again.
            android.util.Log.e("BiometricHelper", "Failed to create EncryptedSharedPreferences", e)
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            
            // Delete the physical file as well
            try {
                val sharedPrefsFile = java.io.File(
                    context.filesDir.parent, 
                    "shared_prefs/$ENCRYPTED_PREFS_NAME.xml"
                )
                if (sharedPrefsFile.exists()) sharedPrefsFile.delete()
            } catch (fileEx: Exception) {}

            // Try one more time (this will generate a new key)
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun saveCredentials(context: Context, email: String, password: String) {
        getEncryptedPrefs(context).edit()
            .putString(KEY_BIOMETRIC_EMAIL, email)
            .putString(KEY_BIOMETRIC_PASSWORD, password)
            .putBoolean(KEY_BIOMETRIC_ENABLED, true)
            .apply()
    }

    fun getCredentials(context: Context): Pair<String?, String?> {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(KEY_BIOMETRIC_EMAIL, null) to prefs.getString(KEY_BIOMETRIC_PASSWORD, null)
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return getEncryptedPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun deleteBiometricAccount(context: Context) {
        getEncryptedPrefs(context).edit()
            .remove(KEY_BIOMETRIC_EMAIL)
            .remove(KEY_BIOMETRIC_PASSWORD)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
    }
}

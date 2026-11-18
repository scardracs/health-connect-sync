package it.healthsync.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStore {
    private const val FILE_NAME = "salute_sync_secure"

    @Volatile
    private var cached: SharedPreferences? = null

    fun open(context: Context): SharedPreferences {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: createWithRecovery(context.applicationContext).also { cached = it }
        }
    }

    @Suppress("DEPRECATION")
    private fun createWithRecovery(context: Context): SharedPreferences {
        return try {
            create(context)
        } catch (_: Exception) {
            context.deleteSharedPreferences(FILE_NAME)
            create(context)
        }
    }

    @Suppress("DEPRECATION")
    private fun create(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}

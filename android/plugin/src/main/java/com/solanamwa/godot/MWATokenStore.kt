package com.solanamwa.godot

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.util.UUID

/**
 * Encrypted auth token storage backed by Android Keystore.
 * GDScript only sees UUID handles; raw tokens stay in Kotlin.
 */
class MWATokenStore(private val context: Context) {

    companion object {
        private const val TAG = "MWATokenStore"
        private const val PREFS_FILE = "solana_mwa_auth_prefs"
        private const val SUFFIX_TOKEN = ".token"
        private const val SUFFIX_IDENTITY = ".identity"
    }

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    /**
     * Stores an auth token encrypted and returns a UUID handle safe for GDScript.
     *
     * @param authToken       Raw auth token from the wallet.
     * @param identityUriHash SHA-256 hex of the dApp identity URI, for reauth binding.
     */
    suspend fun storeToken(authToken: String, identityUriHash: String): String =
        withContext(Dispatchers.IO) {
            val handle = UUID.randomUUID().toString()
            val prefs = getEncryptedPrefs()
            prefs.edit()
                .putString(handle + SUFFIX_TOKEN, authToken)
                .putString(handle + SUFFIX_IDENTITY, identityUriHash)
                .commit()
            Log.d(TAG, "Stored auth token with handle=$handle")
            handle
        }

    /**
     * Retrieves a stored auth token by UUID handle.
     * Returns null if the handle is missing or the identity hash doesn't match.
     */
    suspend fun retrieveToken(handle: String, expectedIdentityUriHash: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val prefs = getEncryptedPrefs()
                val storedIdentity = prefs.getString(handle + SUFFIX_IDENTITY, null)
                if (storedIdentity == null) {
                    Log.w(TAG, "No identity entry for handle=$handle")
                    return@withContext null
                }

                if (storedIdentity != expectedIdentityUriHash) {
                    Log.w(TAG, "Identity hash mismatch for handle=$handle " +
                            "(expected=$expectedIdentityUriHash, stored=$storedIdentity)")
                    return@withContext null
                }

                val token = prefs.getString(handle + SUFFIX_TOKEN, null)
                if (token == null) {
                    Log.w(TAG, "Token entry missing for handle=$handle despite identity present")
                }
                token
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "Decryption failed for handle=$handle, data may be corrupted", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error retrieving token for handle=$handle", e)
                null
            }
        }

    /** Removes the token and identity entries for the given handle. No-op if absent. */
    suspend fun clearToken(handle: String) = withContext(Dispatchers.IO) {
        try {
            val prefs = getEncryptedPrefs()
            prefs.edit()
                .remove(handle + SUFFIX_TOKEN)
                .remove(handle + SUFFIX_IDENTITY)
                .commit()
            Log.d(TAG, "Cleared token for handle=$handle")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing token for handle=$handle", e)
        }
    }

    /** Wipes all stored tokens. Forces re-authorization for every dApp. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            val prefs = getEncryptedPrefs()
            prefs.edit().clear().commit()
            Log.d(TAG, "Cleared all stored auth tokens")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all tokens", e)
        }
    }

    /**
     * Lazily initializes EncryptedSharedPreferences.
     * Tries StrongBox on API 28+, falls back to TEE-backed Keystore.
     * If the prefs file is corrupted (e.g. Keystore wiped), deletes and retries once.
     */
    private fun getEncryptedPrefs(): SharedPreferences {
        cachedPrefs?.let { return it }

        synchronized(this) {
            cachedPrefs?.let { return it }

            val prefs = try {
                createEncryptedPrefs(useStrongBox = true)
            } catch (e: StrongBoxUnavailableException) {
                Log.w(TAG, "StrongBox unavailable, falling back to standard Keystore", e)
                createEncryptedPrefs(useStrongBox = false)
            } catch (e: GeneralSecurityException) {
                Log.e(TAG, "EncryptedSharedPreferences init failed, wiping and retrying", e)
                deletePrefsFile()
                try {
                    createEncryptedPrefs(useStrongBox = false)
                } catch (retryEx: Exception) {
                    Log.e(TAG, "Retry also failed, encrypted storage unavailable", retryEx)
                    throw retryEx
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error creating EncryptedSharedPreferences", e)
                deletePrefsFile()
                createEncryptedPrefs(useStrongBox = false)
            }

            cachedPrefs = prefs
            return prefs
        }
    }

    private fun createEncryptedPrefs(useStrongBox: Boolean): SharedPreferences {
        val masterKeyBuilder = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)

        if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            masterKeyBuilder.setRequestStrongBoxBacked(true)
        }

        val masterKey = masterKeyBuilder.build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Deletes the prefs XML file as last-resort recovery for corrupted encrypted data. */
    private fun deletePrefsFile() {
        try {
            val prefsFile = context.filesDir.parentFile
                ?.resolve("shared_prefs")
                ?.resolve("$PREFS_FILE.xml")
            if (prefsFile != null && prefsFile.exists()) {
                val deleted = prefsFile.delete()
                Log.w(TAG, "Deleted corrupted prefs file: $deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete prefs file", e)
        }
    }
}

package com.catseye.screenblock.data

import android.content.Context
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import com.catseye.screenblock.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val KEY_PASSWORD_HASH = "pattern_hash_key"
private const val KEY_SALT = "salt_key"

class DataStoreManager @Inject constructor (
    @ApplicationContext private val context: Context
) {

    private val saltPrefKey = byteArrayPreferencesKey(KEY_SALT)
    private val hashedKeyPrefKey = byteArrayPreferencesKey(KEY_PASSWORD_HASH)

    fun getStoredKey(): Flow<ByteArray?> {
        return context.dataStore.data.map { preferences ->
            preferences[hashedKeyPrefKey] ?: null
        }
    }

    fun getStoredSalt(): Flow<ByteArray?> {
        return context.dataStore.data.map { preferences ->
                preferences[saltPrefKey] ?: null
        }
    }

    suspend fun storePair(salt: ByteArray, key: ByteArray) {
        context.dataStore.edit { settings ->
            settings[hashedKeyPrefKey] = key
            settings[saltPrefKey] = salt
        }
    }
}
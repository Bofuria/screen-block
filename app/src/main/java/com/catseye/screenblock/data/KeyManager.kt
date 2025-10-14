package com.catseye.screenblock.data

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

private const val PBE_KEY_LENGTH = 256

sealed interface KeyResult {
    object Success: KeyResult
    data class Error(val msg: String): KeyResult
}

class KeyManager @Inject constructor(
    private val dataStoreManager: DataStoreManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var salt: ByteArray? = null
    private var hashedKey: ByteArray? = null

    init {
        scope.launch {
            val saltFlow = dataStoreManager.getStoredSalt()
            val keyFlow = dataStoreManager.getStoredKey()

            saltFlow.combine(keyFlow) { cSalt, cKey ->
                Pair(cSalt, cKey)
            }.collect { pair ->
                salt = pair.first
                hashedKey = pair.second
            }
        }
    }

    suspend fun isKeyPresent(): Boolean {
        return salt != null && hashedKey != null
    }

    suspend fun compareKey(newKey: List<Int>): Boolean {
        val hashedNewKey = getHashedKey(salt!!, newKey)
        return MessageDigest.isEqual(hashedNewKey, hashedKey)
    }

    suspend fun createNewKey(pattern: List<Int>): KeyResult {
        try {
            val newSalt = createSalt()
            val newHashedKey = getHashedKey(newSalt, pattern)
            dataStoreManager.storePair(newHashedKey, newSalt)
            return KeyResult.Success
        } catch (e: Exception) {
            Log.e("KeyError", "Error creating key: $e")
            return KeyResult.Error("Error creating key, try again later.")
        }
    }



    private suspend fun createSalt(size: Int = 16): ByteArray = ByteArray(size).also {
        SecureRandom().nextBytes(it)
    }

    private suspend fun getHashedKey(salt: ByteArray, pattern: List<Int>, iters: Int = 150000): ByteArray /* hashedKey */ {
        val keyCharArray = pattern.joinToString(",").toCharArray()
        Log.d("MyKey", "getHashedKey: ${keyCharArray[0]}")
        val specKey = PBEKeySpec(
            keyCharArray,
            salt,
            iters,
            PBE_KEY_LENGTH
        )

        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specKey).encoded
    }

    // onDestroy vm
    fun close() {
        scope.cancel()
    }
}
package com.catseye.screenblock.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

data class KeyData(
    val key: ByteArray,
    val salt: ByteArray
)

class KeyManager @Inject constructor(
    private val dataStoreManager: DataStoreManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var keyData: Flow<KeyData?>

    init {
        scope.launch {
            keyData = dataStoreManager.getKeyPair().map { pair ->
                pair?.let {
                    Log.d("MyLog", "KeyData: $pair")
                    KeyData(pair.first, pair.second)
                }
            }.distinctUntilChanged()
        }
    }

    fun isKeyPresent(): Flow<Boolean> {
        return dataStoreManager.getKeyPair().map {
            it != null
        }.distinctUntilChanged()
    }


    suspend fun compareKey(newKey: List<Int>): Boolean {
        val kData = keyData.first() ?: return false
        val hashedNewKey = getHashedKey(kData!!.salt, newKey)
        Log.d("Keys", "stored: ${kData!!.key}, entered: $hashedNewKey")
        return MessageDigest.isEqual(hashedNewKey, kData!!.key)

    }

    suspend fun createNewKey(pattern: List<Int>): KeyResult {
        try {
            val newSalt = createSalt()
            val newHashedKey = getHashedKey(newSalt, pattern)
            dataStoreManager.storePair(newSalt, newHashedKey)
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
        Log.d("MyKey", "salt: ${salt.hex()}, pattern: ${pattern}")

        val keyCharArray = pattern.joinToString(",").toCharArray()
        Log.d("MyKey", "getHashedKey: ${pattern}")
        val specKey = PBEKeySpec(
            keyCharArray,
            salt,
            iters,
            PBE_KEY_LENGTH
        )

        val result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specKey).encoded
        Log.d("MyKey", "result: ${result.hex()}")
        return result
    }

    // onDestroy vm
    fun close() {
        scope.cancel()
    }
}

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
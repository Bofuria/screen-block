package com.catseye.screenblock.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catseye.screenblock.data.KeyManager
import com.catseye.screenblock.data.KeyResult
import com.catseye.screenblock.service.OverlayService
import com.pranavpandey.android.dynamic.toasts.DynamicToast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RootScreenState(
    val isOverlayActive: Boolean = false,
    val isKeyEstablished: Boolean = false
)

sealed interface RootScreenEvent {
    data class CreateKeyPattern(val pattern: List<Int>): RootScreenEvent
    data class ToggleFloatingBubble(val state: Boolean): RootScreenEvent
}

@HiltViewModel
class RootViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager
) : ViewModel() {

    private val _state = MutableStateFlow<RootScreenState>(RootScreenState())
    val state = _state.asStateFlow()

    val serviceIntent = Intent(context, OverlayService::class.java)

    init {
        viewModelScope.launch {
            val isKeyPresent = keyManager.isKeyPresent()
            _state.update { it.copy(isKeyEstablished = isKeyPresent) }
        }
    }

    fun onEvent(event: RootScreenEvent) {
        when(event) {
            is RootScreenEvent.CreateKeyPattern -> {
                createKey(event.pattern)
            }

            is RootScreenEvent.ToggleFloatingBubble -> {
                switchToggle(event.state)
            }
        }
    }

    private fun switchToggle(isEnabled: Boolean) {
        if(isEnabled) {
            context.startService(serviceIntent)
            _state.update { it.copy(isOverlayActive = true) }
        } else {
            // or graceful startForegroundService() + .setAction("STOP")
            context.stopService(serviceIntent) // <- service stops himself or here?
            _state.update { it.copy(isOverlayActive = false) }
        }
    }

    private fun createKey(newKey: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            val keyResult = keyManager.createNewKey(newKey)
            Log.d("createKey", "new key RESULT: $keyResult")
            withContext(Dispatchers.Main) {
                when(keyResult) {
                    is KeyResult.Success -> {
                        DynamicToast.makeSuccess(context, "Key added successfully").show()
                        _state.update { it.copy(isKeyEstablished = true) }
                    }
                    is KeyResult.Error -> {
                        DynamicToast.makeError(context, keyResult.msg).show()
                    }
                }
            }
        }
    }

}
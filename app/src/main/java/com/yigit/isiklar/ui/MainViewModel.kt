package com.yigit.isiklar.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yigit.isiklar.data.LightController
import com.yigit.isiklar.model.LightDevice
import com.yigit.isiklar.model.Rgb
import com.yigit.isiklar.model.Scene
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = LightController.get(app)

    val devices: StateFlow<List<LightDevice>> = controller.devices
    val scenes: StateFlow<List<Scene>> = controller.scenes

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    var apiKey by mutableStateOf(controller.store.apiKey)
        private set

    init {
        refresh()
    }

    fun refresh() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                controller.refresh()?.let { _messages.emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Yenileme başarısız")
            } finally {
                _busy.value = false
            }
        }
    }

    fun setPower(id: String, on: Boolean) = act { controller.setPower(id, on) }
    fun setBrightness(id: String, value: Int) = act { controller.setBrightness(id, value) }
    fun setColor(id: String, rgb: Rgb) = act { controller.setColor(id, rgb) }
    fun setKelvin(id: String, kelvin: Int) = act { controller.setKelvin(id, kelvin) }
    fun allPower(on: Boolean) = act { controller.allPower(on) }
    fun applyScene(scene: Scene) = act { controller.applyScene(scene) }

    fun saveScene(name: String) {
        controller.saveCurrentAsScene(name)
        _messages.tryEmit("\"${name.trim()}\" sahnesi kaydedildi")
    }

    fun deleteScene(scene: Scene) {
        controller.deleteScene(scene.id)
    }

    fun saveApiKey(key: String) {
        controller.setApiKey(key)
        apiKey = key.trim()
        refresh()
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.message ?: "İşlem başarısız")
            }
        }
    }
}

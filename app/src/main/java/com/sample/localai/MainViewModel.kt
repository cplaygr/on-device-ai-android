package com.sample.localai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(private val llmEngine: LocalLlmEngine) : ViewModel() {

    private val TAG = "MainViewModel"
    private val _uiState = MutableStateFlow("Hello World (Initializing...)")
    val uiState: StateFlow<String> = _uiState.asStateFlow()

    private val _countdown = MutableStateFlow<Int?>(null)
    val countdown: StateFlow<Int?> = _countdown.asStateFlow()

    init {
        viewModelScope.launch {
            llmEngine.initialize()
            _uiState.value = llmEngine.generateRandomGreeting()
            startPeriodicUpdates()
        }
    }

    private fun startPeriodicUpdates() {
        viewModelScope.launch {
            while (isActive) {
                for (remaining in 5 downTo 1) {
                    _countdown.value = remaining
                    delay(1_000)
                }
                _countdown.value = null
                _uiState.value = "Thinking..." // 로딩 상태 인디케이터

                val newText = llmEngine.generateRandomGreeting()
                Log.d(TAG, "New greeting received: $newText")
                _uiState.value = newText
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmEngine.close() // 메모리 누수 방지
    }
}
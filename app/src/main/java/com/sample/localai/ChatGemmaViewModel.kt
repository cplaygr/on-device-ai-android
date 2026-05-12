package com.sample.localai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isInitializing: Boolean = true,
    val isGenerating: Boolean = false
)

class ChatGemmaViewModel(private val llmEngine: LocalLlmEngine) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            llmEngine.initialize()
            _state.update { it.copy(isInitializing = false) }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_state.value.isGenerating || _state.value.isInitializing) return

        val userMessage = ChatMessage(text = trimmed, isUser = true)
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                isGenerating = true
            )
        }

        viewModelScope.launch {
            val history = _state.value.messages
            val response = llmEngine.generateChatResponse(history).trim()
            val botMessage = ChatMessage(text = response, isUser = false)
            _state.update {
                it.copy(
                    messages = it.messages + botMessage,
                    isGenerating = false
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmEngine.close()
    }
}

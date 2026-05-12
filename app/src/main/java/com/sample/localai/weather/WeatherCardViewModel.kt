package com.sample.localai.weather

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sample.localai.LocalLlmEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeatherUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val weather: WeatherData? = null,
    val currentCard: WeatherCardType = WeatherCardType.CURRENT_FUN,
    val gemmaComment: String = "",
    val isCommenting: Boolean = false
)

class WeatherCardViewModel(
    private val llmEngine: LocalLlmEngine,
    private val repository: WeatherRepository = WeatherRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private var deck: ArrayDeque<WeatherCardType> = ArrayDeque()

    init {
        viewModelScope.launch {
            llmEngine.initialize()
            loadWeather()
        }
    }

    private suspend fun loadWeather() {
        _state.update { it.copy(isLoading = true, error = null) }
        try {
            val data = repository.fetch()
            refillDeck()
            val first = deck.removeFirst()
            _state.update {
                it.copy(
                    isLoading = false,
                    weather = data,
                    currentCard = first
                )
            }
            commentOnCurrent()
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = "날씨 정보를 가져오지 못했어요: ${t.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    fun shuffleNextCard() {
        if (_state.value.isLoading || _state.value.isCommenting) return
        if (deck.isEmpty()) refillDeck(exclude = _state.value.currentCard)
        val next = deck.removeFirst()
        _state.update { it.copy(currentCard = next, gemmaComment = "") }
        commentOnCurrent()
    }

    fun retry() {
        viewModelScope.launch { loadWeather() }
    }

    private fun refillDeck(exclude: WeatherCardType? = null) {
        val all = WeatherCardType.entries.toMutableList()
        if (exclude != null) all.remove(exclude)
        all.shuffle()
        deck = ArrayDeque(all)
    }

    private fun commentOnCurrent() {
        val weather = _state.value.weather ?: return
        val card = _state.value.currentCard
        val context = buildContextFor(card, weather)
        _state.update { it.copy(isCommenting = true) }
        viewModelScope.launch {
            Log.d("WeatherCardViewModel", "Generating comment for card: $card")
            val comment = llmEngine.generateWeatherComment(context)
            Log.d("WeatherCardViewModel", "Received comment: $comment")
            _state.update { it.copy(gemmaComment = comment, isCommenting = false) }
        }
    }

    private fun buildContextFor(card: WeatherCardType, w: WeatherData): String = when (card) {
        WeatherCardType.CURRENT_FUN ->
            "지금 서울 날씨는 ${weatherCodeToText(w.currentWeatherCode)}이고, " +
                "기온은 ${"%.1f".format(w.currentTemp)}도야."
        WeatherCardType.HOURLY -> {
            val temps = w.hourly.take(6).joinToString(", ") {
                "${it.hourLabel} ${"%.0f".format(it.temp)}도(${weatherCodeToText(it.weatherCode)})"
            }
            "앞으로 시간별 날씨: $temps."
        }
        WeatherCardType.WEEKLY -> {
            val days = w.daily.take(7).joinToString(", ") {
                "${it.dayLabel} ${"%.0f".format(it.minTemp)}~${"%.0f".format(it.maxTemp)}도(${weatherCodeToText(it.weatherCode)})"
            }
            "이번 주 날씨: $days."
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmEngine.close()
    }
}

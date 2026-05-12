package com.sample.localai.weather

data class WeatherData(
    val currentTemp: Double,
    val currentWeatherCode: Int,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>
)

data class HourlyPoint(
    val hourLabel: String,
    val temp: Double,
    val weatherCode: Int
)

data class DailyPoint(
    val dayLabel: String,
    val maxTemp: Double,
    val minTemp: Double,
    val weatherCode: Int
)

enum class WeatherCardType {
    CURRENT_FUN,
    HOURLY,
    WEEKLY
}

fun weatherCodeToEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67 -> "🌧️"
    in 71..77 -> "❄️"
    in 80..82 -> "🌧️"
    in 85..86 -> "🌨️"
    in 95..99 -> "⛈️"
    else -> "🌡️"
}

fun weatherCodeToText(code: Int): String = when (code) {
    0 -> "맑음"
    1, 2 -> "대체로 맑음"
    3 -> "흐림"
    45, 48 -> "안개"
    in 51..57 -> "이슬비"
    in 61..67 -> "비"
    in 71..77 -> "눈"
    in 80..82 -> "소나기"
    in 85..86 -> "눈 소나기"
    in 95..99 -> "뇌우"
    else -> "알 수 없음"
}

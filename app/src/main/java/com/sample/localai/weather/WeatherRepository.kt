package com.sample.localai.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

class WeatherRepository(
    private val latitude: Double = 37.5665, // 서울 기본값
    private val longitude: Double = 126.9780,
    private val timezone: String = "Asia/Seoul"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun fetch(): WeatherData = withContext(Dispatchers.IO) {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                "&forecast_days=7" +
                "&timezone=$timezone"
        )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val response = json.decodeFromString<OpenMeteoResponse>(body)

        response.toDomain()
    }
}

private fun OpenMeteoResponse.toDomain(): WeatherData {
    val hourlyPoints = hourly.toHourlyPoints(current.time)
    val dailyPoints = daily.toDailyPoints()

    return WeatherData(
        currentTemp = current.temperature,
        currentWeatherCode = current.weatherCode,
        hourly = hourlyPoints,
        daily = dailyPoints
    )
}

private fun HourlyBlock.toHourlyPoints(nowIso: String): List<HourlyPoint> {
    val startIdx = time.indexOfFirst { it >= nowIso }.takeIf { it >= 0 } ?: 0
    val endIdx = minOf(startIdx + 12, time.size)
    return (startIdx until endIdx).map { i ->
        HourlyPoint(
            hourLabel = time[i].substringAfter('T').substring(0, 2) + "시",
            temp = temperature[i],
            weatherCode = weatherCode[i]
        )
    }
}

private fun DailyBlock.toDailyPoints(): List<DailyPoint> {
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
    return time.indices.map { i ->
        val (y, m, d) = time[i].split("-").map { it.toInt() }
        DailyPoint(
            dayLabel = "${dayNames[dayOfWeekIndex(y, m, d)]} ${m}/${d}",
            maxTemp = maxTemp[i],
            minTemp = minTemp[i],
            weatherCode = weatherCode[i]
        )
    }
}

// Zeller's congruence(그레고리력) — 외부 라이브러리 없이 요일 계산 (0=일 ~ 6=토)
private fun dayOfWeekIndex(year: Int, month: Int, day: Int): Int {
    val (y, m) = if (month < 3) year - 1 to month + 12 else year to month
    val k = y % 100
    val j = y / 100
    val h = (day + (13 * (m + 1)) / 5 + k + k / 4 + j / 4 + 5 * j) % 7
    return (h + 6) % 7
}

@Serializable
private data class OpenMeteoResponse(
    val current: CurrentBlock,
    val hourly: HourlyBlock,
    val daily: DailyBlock
)

@Serializable
private data class CurrentBlock(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("weather_code") val weatherCode: Int
)

@Serializable
private data class HourlyBlock(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>
)

@Serializable
private data class DailyBlock(
    val time: List<String>,
    @SerialName("temperature_2m_max") val maxTemp: List<Double>,
    @SerialName("temperature_2m_min") val minTemp: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>
)

package com.sample.localai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sample.localai.weather.DailyPoint
import com.sample.localai.weather.HourlyPoint
import com.sample.localai.weather.WeatherCardType
import com.sample.localai.weather.WeatherCardViewModel
import com.sample.localai.weather.WeatherData
import com.sample.localai.weather.weatherCodeToEmoji
import com.sample.localai.weather.weatherCodeToText

@Composable
fun WeatherCardScreen(modifier: Modifier = Modifier) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: WeatherCardViewModel = viewModel {
        WeatherCardViewModel(LocalLlmEngine(appContext))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoading -> LoadingView()
            state.error != null -> ErrorView(state.error!!, onRetry = viewModel::retry)
            state.weather != null -> WeatherCardContent(
                weather = state.weather!!,
                cardType = state.currentCard,
                gemmaComment = state.gemmaComment,
                isCommenting = state.isCommenting,
                onTap = viewModel::shuffleNextCard
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Open-Meteo에서 날씨를 가져오는 중...")
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("다시 시도") }
    }
}

@Composable
private fun WeatherCardContent(
    weather: WeatherData,
    cardType: WeatherCardType,
    gemmaComment: String,
    isCommenting: Boolean,
    onTap: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = cardType,
            transitionSpec = {
                (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.92f, animationSpec = tween(500))) togetherWith
                    (fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.92f, animationSpec = tween(300)))
            },
            label = "weatherCard"
        ) { type ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                when (type) {
                    WeatherCardType.CURRENT_FUN -> CurrentFunCard(weather, gemmaComment, isCommenting)
                    WeatherCardType.HOURLY -> HourlyCard(weather.hourly, gemmaComment, isCommenting)
                    WeatherCardType.WEEKLY -> WeeklyCard(weather.daily, gemmaComment, isCommenting)
                }
            }
        }
        Text(
            text = "카드를 탭하면 Gemma가 다른 시점의 날씨로 안내해요",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CurrentFunCard(weather: WeatherData, comment: String, isCommenting: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("지금 날씨", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text(weatherCodeToEmoji(weather.currentWeatherCode), style = MaterialTheme.typography.displayLarge)
        Text(
            text = "${"%.1f".format(weather.currentTemp)}°C",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        Text(weatherCodeToText(weather.currentWeatherCode), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(20.dp))
        GemmaCommentBlock(comment, isCommenting)
    }
}

@Composable
private fun HourlyCard(hourly: List<HourlyPoint>, comment: String, isCommenting: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text("시간별 온도", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        val maxTemp = hourly.maxOfOrNull { it.temp } ?: 1.0
        val minTemp = hourly.minOfOrNull { it.temp } ?: 0.0
        val range = (maxTemp - minTemp).coerceAtLeast(1.0)
        hourly.take(8).forEach { point ->
            val ratio = ((point.temp - minTemp) / range).toFloat().coerceIn(0.05f, 1f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(point.hourLabel, modifier = Modifier.width(48.dp), style = MaterialTheme.typography.bodyMedium)
                Text(weatherCodeToEmoji(point.weatherCode), modifier = Modifier.width(36.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("${"%.0f".format(point.temp)}°", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        GemmaCommentBlock(comment, isCommenting)
    }
}

@Composable
private fun WeeklyCard(daily: List<DailyPoint>, comment: String, isCommenting: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text("한 주간 날씨", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        daily.take(7).forEach { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(day.dayLabel, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
                Text(weatherCodeToEmoji(day.weatherCode), modifier = Modifier.width(40.dp), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${"%.0f".format(day.minTemp)}° / ${"%.0f".format(day.maxTemp)}°",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        GemmaCommentBlock(comment, isCommenting)
    }
}

@Composable
private fun GemmaCommentBlock(comment: String, isCommenting: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center
        ) {
            Text("G", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (isCommenting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Gemma가 한마디 준비중...", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                text = comment.ifBlank { "..." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

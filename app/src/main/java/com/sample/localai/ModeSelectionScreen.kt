package com.sample.localai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class AppMode(val title: String, val description: String) {
    HELLO_WORLD(
        title = "1. Hello World 테스트",
        description = "Gemma가 생성한 인사말을 10초마다 보여줍니다."
    ),
    CHAT_GEMMA(
        title = "2. Chat Gemma",
        description = "ChatGPT처럼 Gemma와 자유롭게 대화합니다."
    ),
    WEATHER_CARD(
        title = "3. Gemma 날씨 카드",
        description = "Open-Meteo + Gemma가 만들어내는 날씨 카드 UX."
    )
}

@Composable
fun ModeSelectionScreen(
    onModeSelected: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = "모드 선택",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        AppMode.entries.forEach { mode ->
            ModeCard(mode = mode, onClick = { onModeSelected(mode) })
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ModeCard(mode: AppMode, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = mode.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = mode.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

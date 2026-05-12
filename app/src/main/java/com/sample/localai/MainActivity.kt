package com.sample.localai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sample.localai.ui.theme.LocalAISampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalAISampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    var selectedMode by remember { mutableStateOf<AppMode?>(null) }

    BackHandler(enabled = selectedMode != null) {
        selectedMode = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (selectedMode) {
            null -> ModeSelectionScreen(onModeSelected = { selectedMode = it })
            AppMode.HELLO_WORLD -> {
                val appContext = LocalContext.current.applicationContext
                val mainViewModel: MainViewModel = viewModel {
                    MainViewModel(LocalLlmEngine(appContext))
                }
                AiGreetingScreen(mainViewModel)
            }
            AppMode.CHAT_GEMMA -> ChatGemmaScreen()
            AppMode.WEATHER_CARD -> WeatherCardScreen()
        }
    }
}

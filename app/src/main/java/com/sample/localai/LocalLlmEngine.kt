package com.sample.localai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalLlmEngine(
    private val context: Context
) {
    private var llmInference: LlmInference? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (llmInference != null) return@withContext

        val modelName = "gemma-1.1-2b-it-cpu-int4.bin"
        val destFile = File(context.filesDir, modelName)

        // assets에서 복사해오기 (최초 1회)
        if (!destFile.exists()) {
            context.assets.open(modelName).use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }

        // 내부 저장소에 안전하게 복사된 파일의 절대 경로 사용
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(destFile.absolutePath)
            .setMaxTokens(512)
            .setTemperature(0.8f) // 1분마다 무작위 문구를 위해 0.8 정도로 설정
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
    }

    suspend fun generateRandomGreeting(): String = withContext(Dispatchers.IO) {
        val promt = "Respond with a single, random and creative greeting message. Do not use quotes."
        return@withContext llmInference?.generateResponse(promt) ?: "Engine not initialized."
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
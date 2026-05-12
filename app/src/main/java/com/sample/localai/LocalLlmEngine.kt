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
        val promt = "따옴표를 사용하지 말고, 무작위로 창의적인 인사말을 하나 작성해 주세요."
        return@withContext llmInference?.generateResponse(promt) ?: "Engine not initialized."
    }

    suspend fun generateWeatherComment(context: String): String = withContext(Dispatchers.IO) {
        val engine = llmInference ?: return@withContext "오늘도 즐거운 하루 보내세요!"

        val prompt = """
            너는 위트있는 한국어 날씨 친구야.
            아래 정보를 보고, 따옴표나 이모지 없이 한국어 한 문장으로
            재미있고 따뜻한 한마디만 답해줘. 15자 이상 40자 이하로 작성해.

            정보:
            $context

            한마디:
        """.trimIndent()

        repeat(2) {
            val raw = engine.generateResponse(prompt)?.trim().orEmpty()
            val cleaned = raw
                .removePrefix("한마디:")
                .removePrefix("\"")
                .removeSuffix("\"")
                .trim()
            if (isMeaningfulComment(cleaned)) return@withContext cleaned
        }
        return@withContext "오늘도 즐거운 하루 보내세요!"
    }

    private fun isMeaningfulComment(text: String): Boolean {
        if (text.length < 5) return false
        val letters = text.count { it.isLetter() }
        return letters >= 3
    }

    suspend fun generateChatResponse(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val prompt = buildString {
            history.forEach { msg ->
                val role = if (msg.isUser) "user" else "model"
                append("<start_of_turn>")
                append(role)
                append('\n')
                append(msg.text)
                append("<end_of_turn>\n")
            }
            append("<start_of_turn>model\n")
        }
        return@withContext llmInference?.generateResponse(prompt) ?: "Engine not initialized."
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
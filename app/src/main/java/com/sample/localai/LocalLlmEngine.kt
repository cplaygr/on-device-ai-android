package com.sample.localai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalLlmEngine(
    private val context: Context
) {
    private val TAG = "LocalLlmEngine"
    private var llmInference: LlmInference? = null
    private var isLegacyBin: Boolean = false

    private var chatSession: LlmInferenceSession? = null
    private var chatPrimed: Boolean = false

    private val chatSystemPreamble: String = """
        너는 친절하고 간결한 한국어 도우미야.
        규칙:
        - 한국어로만 답해.
        - 한 번 답한 문장이나 표현을 같은 답변 안에서 반복하지 마.
        - 불필요한 인사말은 빼고 핵심부터 말해.
        - 답변은 2~4문장으로 짧게 유지해.
    """.trimIndent()

    // .bin 런타임은 temperature <= 1.0 강제
    private fun Float.clampTemperature() = if (isLegacyBin) minOf(this, 1.0f) else this

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (llmInference != null) return@withContext

        val modelName = resolveModelAssetName()
        isLegacyBin = modelName.endsWith(".bin", ignoreCase = true)
        val destFile = File(context.filesDir, modelName)

        val expectedSize: Long? = runCatching {
            context.assets.openFd(modelName).use { it.length }
        }.getOrNull()

        val needsCopy = when {
            !destFile.exists() -> true
            expectedSize != null && destFile.length() != expectedSize -> true
            else -> false
        }
        if (needsCopy) {
            context.assets.open(modelName).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        // .bin: temperature는 LlmInferenceOptions에서 설정 불가능한 버전일 수 있음
        // .task: LlmInferenceOptions는 모델 로딩 전용, temperature는 세션에서 설정
        val optionsBuilder = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(destFile.absolutePath)
            .setMaxTokens(1024)

        llmInference = LlmInference.createFromOptions(context, optionsBuilder.build())
        Log.d(TAG, "Initialized model: $modelName (Legacy: $isLegacyBin)")
    }

    suspend fun generateRandomGreeting(): String = withContext(Dispatchers.IO) {
        val topics = listOf("기쁨", "희망", "행운", "친절", "창의성", "휴식", "평온", "열정")
        val selectedTopic = topics.random()
        val prompt = if (isLegacyBin) {
            "따옴표를 사용하지 말고, $selectedTopic 과 관련된 창의적인 한국어 인사말을 한 문장으로 작성해 주세요."
        } else {
            """
            <start_of_turn>user
            $selectedTopic 과 관련된 창의적인 한국어 인사말을 한 문장으로 작성해 주세요. 따옴표는 사용하지 마세요.
            <end_of_turn>
            <start_of_turn>model
            """.trimIndent()
        }

        generate(temperature = 1.0f, topK = 64, topP = 0.95f) { prompt }.ifBlank { "오늘도 멋진 하루 되세요!" }
    }

    suspend fun generateWeatherComment(weatherContext: String): String = withContext(Dispatchers.IO) {
        val moods = listOf("유머러스한", "감성적인", "츤데레 같은", "활기찬", "차분한", "엉뚱한", "진지한", "따뜻한")
        val selectedMood = moods.random()

        val promptContent = """
            너는 $selectedMood 한국어 날씨 친구야.
            아래 정보를 참고해서, 따옴표나 이모지 없이 한국어 한 문장으로
            재미있고 창의적인 한마디를 답해줘. 15자 이상 45자 이하로 작성해.
            기존에 했던 말은 하지 말고 매번 새로운 표현을 써야 해.

            정보:
            $weatherContext
        """.trimIndent()

        val prompt = if (isLegacyBin) {
            "$promptContent\n\n한마디:"
        } else {
            """
            <start_of_turn>user
            $promptContent
            <end_of_turn>
            <start_of_turn>model
            """.trimIndent()
        }

        repeat(2) {
            // 온도를 1.0으로 높여 무작위성 강화
            val raw = generate(temperature = 1.0f, topK = 64, topP = 0.95f) { prompt }
            val cleaned = raw
                .removePrefix("한마디:")
                .removePrefix("\"")
                .removeSuffix("\"")
                .trim()
            if (isMeaningfulComment(cleaned)) return@withContext cleaned
        }
        "날씨가 어떻든 당신의 하루는 빛날 거예요!"
    }

    suspend fun chat(userText: String): String = withContext(Dispatchers.IO) {
        val engine = llmInference ?: return@withContext "Engine not initialized."

        val firstTurnUser = if (!chatPrimed) {
            chatPrimed = true
            "$chatSystemPreamble\n\n$userText"
        } else {
            userText
        }
        val turn = "<start_of_turn>user\n$firstTurnUser<end_of_turn>\n<start_of_turn>model\n"
        Log.d(TAG, "Chat prompt turn: $turn")

        val response = if (isLegacyBin) {
            // .bin: 세션 API 없음 — 매 턴마다 전체 히스토리 프롬프트 방식
            val history = buildString {
                chatHistory.forEach { append(it) }
                append(turn)
            }
            val raw = engine.generateResponse(history) ?: ""
            Log.d(TAG, "Chat raw response (Legacy): $raw")
            chatHistory.add(turn)
            chatHistory.add("<start_of_turn>model\n$raw<end_of_turn>\n")
            sanitizeChatResponse(raw)
        } else {
            // .task: 영구 세션으로 KV 캐시 재사용
            val session = chatSession ?: createSession(
                engine, temperature = 0.7f, topK = 40, topP = 0.95f
            ).also { chatSession = it }
            session.addQueryChunk(turn)
            val raw = session.generateResponse()
            Log.d(TAG, "Chat raw response: $raw")
            sanitizeChatResponse(raw)
        }

        Log.d(TAG, "Chat final response: $response")
        response
    }

    fun resetChatSession() {
        chatSession?.close()
        chatSession = null
        chatHistory.clear()
        chatPrimed = false
    }

    // .bin 모드 히스토리 (세션 없으므로 직접 관리)
    private val chatHistory = mutableListOf<String>()

    private fun generate(temperature: Float, topK: Int, topP: Float, prompt: () -> String): String {
        val engine = llmInference ?: return ""
        val promptText = prompt()
        Log.d(TAG, "Generate prompt: $promptText")

        return if (isLegacyBin) {
            // .bin 모델은 세션 API를 지원하지 않는 경우가 많음 -> 직접 호출
            // 주의: .bin 방식은 LlmInferenceOptions에 설정된 temperature를 따름 (동적 변경 불가)
            val raw = engine.generateResponse(promptText) ?: ""
            Log.d(TAG, "Generate raw response (Legacy): $raw")
            raw.trim()
        } else {
            // .task 모델은 세션 API 사용 권장
            val session = createSession(engine, temperature, topK, topP)
            try {
                session.addQueryChunk(promptText)
                val raw = session.generateResponse()
                Log.d(TAG, "Generate raw response (Session): $raw")
                val response = sanitizeChatResponse(raw)
                Log.d(TAG, "Generate final response: $response")
                response
            } finally {
                session.close()
            }
        }
    }

    private fun createSession(
        engine: LlmInference,
        temperature: Float,
        topK: Int,
        topP: Float
    ): LlmInferenceSession {
        val opts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(temperature.clampTemperature())
            .setTopK(topK)
            .setTopP(topP)
            .build()
        return LlmInferenceSession.createFromOptions(engine, opts)
    }

    private fun sanitizeChatResponse(raw: String): String {
        var text = raw
        listOf("<start_of_turn>", "<end_of_turn>", "<|user|>", "<|assistant|>").forEach { token ->
            val idx = text.indexOf(token)
            if (idx >= 0) text = text.substring(0, idx)
        }
        text = text.trim()

        val sentences = text.split(Regex("(?<=[.!?。！？])\\s+"))
        val seen = linkedSetOf<String>()
        val out = StringBuilder()
        for (s in sentences) {
            val key = s.trim()
            if (key.isEmpty()) continue
            if (!seen.add(key)) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(key)
        }
        return out.toString().ifBlank { text }
    }

    private fun resolveModelAssetName(): String {
        val assets = context.assets.list("")?.toList().orEmpty()
        val task = assets.firstOrNull { it.endsWith(".task", ignoreCase = true) }
        val bin = assets.firstOrNull { it.endsWith(".bin", ignoreCase = true) }
        return task ?: bin
            ?: error("assets/ 에 .task 또는 .bin 모델 파일이 없습니다.")
    }

    private fun isMeaningfulComment(text: String): Boolean {
        if (text.length < 5) return false
        return text.count { it.isLetter() } >= 3
    }

    fun close() {
        chatSession?.close()
        chatSession = null
        llmInference?.close()
        llmInference = null
    }

    companion object {
        private const val DEFAULT_BIN_TEMPERATURE = 0.8f
    }
}

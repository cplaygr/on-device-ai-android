package com.sample.localai

sealed interface LlmModelType {
    val fileName: String?

    data object Gemma1_1_2b : LlmModelType {
        override val fileName = "gemma-1.1-2b-it-cpu-int4.bin"
    }

    data object Gemma3n_E2b : LlmModelType {
        override val fileName = "gemma-3n-E2B-it-int4.task"
    }

    data object AutoDetect : LlmModelType {
        override val fileName = null
    }

    data class Custom(override val fileName: String) : LlmModelType
}
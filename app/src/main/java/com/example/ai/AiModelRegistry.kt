package com.example.ai

/**
 * Central model registry so Gemini models can be updated cleanly in a single place
 * as newer specialized models become available.
 */
object AiModelRegistry {
    const val TRANSCRIPTION_MODEL = "gemini-3.8-flash"
    const val TRANSLATION_MODEL = "gemini-3.8-flash"
    const val EDITORIAL_REASONING_MODEL = "gemini-3.8-flash"
    const val FAST_REWRITE_MODEL = "gemini-3.5-flash-lite"
}

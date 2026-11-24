package com.example.ai

import com.example.model.AudioRecording
import com.example.model.BookProject
import com.example.model.ChapterAiAction
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

class GeminiLiteraryEditorService {

    private val editorialModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelRegistry.EDITORIAL_REASONING_MODEL,
            generationConfig = generationConfig {
                temperature = 0.35f
            }
        )
    }

    private val rewriteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelRegistry.FAST_REWRITE_MODEL,
            generationConfig = generationConfig {
                temperature = 0.25f
            }
        )
    }

    fun generateChapterStream(
        book: BookProject,
        outline: ProposedChapterOutline,
        recordings: List<AudioRecording>
    ): Flow<String> = flow {
        val relevantRecordings = outline.sourceRecordingNumbers
            .mapNotNull { num -> recordings.getOrNull(num - 1) }
            .ifEmpty { recordings }

        val sourceText = buildString {
            relevantRecordings.forEach { rec ->
                appendLine("--- Fonte: ${rec.title} ---")
                appendLine(rec.editorialTranscript.ifBlank { rec.verbatimTranscript }.take(18000))
                appendLine()
            }
        }

        val langRule = if (book.targetLanguage == "original") {
            "Escreva no idioma original das gravações."
        } else {
            "Escreva em Português Brasileiro culto, claro, coeso e natural."
        }

        val prompt = """
            Você é um editor literário profissional da LIVRO IA. Redija integralmente o capítulo abaixo com qualidade de livro pronto para publicação.

            Livro: "${book.title}"
            Assunto: "${book.subject}"
            Capítulo ${outline.chapterNumber}: "${outline.title}"
            Subtítulo: "${outline.subtitle}"
            Parte: "${outline.partTitle}"
            Escopo do capítulo: "${outline.summary}"

            DIRETRIZES EDITORIAIS OBRIGATÓRIAS:
            1. $langRule
            2. Transforme a linguagem falada das fontes em prosa literária bem estruturada: introdução ao tema do capítulo, desenvolvimento com subtítulos marcados por '## Subtítulo' quando apropriado, e fechamento ou transição natural.
            3. NÃO resuma excessivamente: preserve exemplos, histórias, ilustrações, citações e argumentos reais do autor.
            4. Preserve a identidade, opinião e estilo do autor (sem tornar um estilo simples artificialmente rebuscado).
            5. NÃO invente argumentos, citações ou fatos que não estejam no material de origem.
            6. Se encontrar marcadores [REVISAR], mantenha o marcador para conferência humana.
            7. Retorne APENAS o texto corrido do capítulo (com subtítulos '## ...' quando cabível), sem repetir o título principal do capítulo no topo.

            MATERIAL TRANSCRITO DE ORIGEM:
            $sourceText
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            editorialModel.generateContentStream(prompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("O modelo não gerou conteúdo para o capítulo ${outline.chapterNumber}.")
        }
    }

    suspend fun rewritePassage(
        passage: String,
        action: ChapterAiAction,
        bookTitle: String,
        chapterTitle: String
    ): String {
        require(passage.isNotBlank()) { "Selecione ou informe um trecho não vazio para edição." }
        val prompt = """
            Você é o revisor editorial da LIVRO IA.
            Livro: "$bookTitle" | Capítulo: "$chapterTitle"
            Ação solicitada: ${action.labelPt}
            Instrução específica: ${action.instructionPt}

            Regras:
            - Mantenha total fidelidade às ideias do autor.
            - Não invente informações, exemplos ou citações externas.
            - Retorne APENAS o texto revisado, pronto para substituir o trecho original (sem explicações antes ou depois).

            TRECHO ORIGINAL:
            $passage
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                rewriteModel.generateContent(prompt)
            }
            val result = response.text?.trim()
            if (result.isNullOrBlank()) {
                throw IllegalStateException("Nenhuma sugestão foi retornada pelo modelo.")
            }
            result
        } catch (e: CancellationException) {
            throw e
        }
    }
}

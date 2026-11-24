package com.example.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class TranscriptionResult(
    val detectedLanguage: String,
    val verbatimTranscript: String,
    val editorialTranscript: String,
    val contentSummary: String,
    val keyTopics: List<String>
)

class GeminiTranscriptionService {

    private val transcriptionSchema = Schema.obj(
        properties = mapOf(
            "detectedLanguage" to Schema.string(description = "Idioma original identificado no áudio, ex: Português, Inglês, Espanhol"),
            "verbatimTranscript" to Schema.string(description = "Transcrição fiel à fala original com pontuação e identificação de falantes quando possível, marcando trechos inaudíveis como [REVISAR]"),
            "editorialTranscript" to Schema.string(description = "Versão em linguagem escrita editorial organizada em parágrafos claros, sem resumir nem inventar conteúdo"),
            "contentSummary" to Schema.string(description = "Resumo objetivo do conteúdo desta gravação para organização editorial"),
            "keyTopics" to Schema.array(
                items = Schema.string(),
                description = "Lista curta dos principais temas abordados"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelRegistry.TRANSCRIPTION_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = transcriptionSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun transcribeChunk(
        chunk: AudioChunk,
        recordingTitle: String,
        bookSubject: String,
        translateToPtBr: Boolean
    ): TranscriptionResult {
        val translationDirective = if (translateToPtBr) {
            """
            Se o áudio estiver em inglês, espanhol ou outro idioma diferente do português brasileiro:
            - Identifique o idioma original em detectedLanguage.
            - Traduza integralmente para português brasileiro natural tanto a transcrição fiel quanto o texto editorial.
            - Preserve nomes próprios, termos técnicos, referências bíblicas e citações.
            - Não adapte nem distorça o pensamento do autor e evite traduções literais que prejudiquem a compreensão.
            """.trimIndent()
        } else {
            """
            Mantenha o idioma original falado no áudio sem traduzir.
            """.trimIndent()
        }

        val prompt = """
            Você é um transcritor e preparador editorial sênior da plataforma LIVRO IA.
            Contexto do livro: "$bookSubject".
            Título da gravação: "$recordingTitle" (Parte ${chunk.index + 1} de ${chunk.totalChunks}).

            REGRAS ESTRITAS DE FIDELIDADE:
            1. Transcreva integralmente o conteúdo falado sem resumir e sem omitir exemplos, histórias, explicações ou argumentos.
            2. NÃO invente frases, citações, dados ou acontecimentos.
            3. Quando uma palavra ou trecho estiver inaudível ou incerto, insira exatamente o marcador [REVISAR] no local em vez de adivinhar.
            4. Identifique falantes diferentes (ex: "Palestrante:", "Entrevistado:") quando houver mais de uma voz.
            5. Em 'verbatimTranscript': entregue a transcrição fiel com pontuação correta, parágrafos organizados e vícios de linguagem removidos apenas se não prejudicarem o sentido ou o estilo do autor.
            6. Em 'editorialTranscript': transforme a fala em texto escrito fluido, coeso e bem pontuado, mantendo 100% da voz, opinião e argumentos originais.
            $translationDirective
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        inlineData(chunk.bytes, chunk.mimeType)
                        text(prompt)
                    }
                )
            }
            val rawJson = response.text
                ?: throw IllegalStateException("O modelo retornou resposta vazia na transcrição.")
            val json = JSONObject(rawJson)
            val topicsArr = json.optJSONArray("keyTopics")
            val topics = if (topicsArr != null) {
                (0 until topicsArr.length()).mapNotNull { topicsArr.optString(it).takeIf { s -> s.isNotBlank() } }.take(25)
            } else emptyList()

            TranscriptionResult(
                detectedLanguage = json.optString("detectedLanguage", "Não identificado"),
                verbatimTranscript = json.optString("verbatimTranscript", ""),
                editorialTranscript = json.optString("editorialTranscript", ""),
                contentSummary = json.optString("contentSummary", ""),
                keyTopics = topics
            )
        } catch (e: CancellationException) {
            throw e
        }
    }
}

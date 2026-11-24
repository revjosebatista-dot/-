package com.example.ai

import com.example.model.AudioRecording
import com.example.model.BookProject
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class ProposedChapterOutline(
    val partTitle: String,
    val chapterNumber: Int,
    val title: String,
    val subtitle: String,
    val summary: String,
    val sourceRecordingNumbers: List<Int>
)

data class ProposedBookStructure(
    val title: String,
    val subtitle: String,
    val presentation: String,
    val dedicationSuggestion: String,
    val acknowledgmentsSuggestion: String,
    val prefaceSuggestion: String,
    val introduction: String,
    val conclusion: String,
    val finalRemarks: String,
    val bibliography: String,
    val editorialNotes: String,
    val chapters: List<ProposedChapterOutline>
)

class GeminiStructureService {

    private val structureSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Título proposto para o livro"),
            "subtitle" to Schema.string(description = "Subtítulo editorial"),
            "presentation" to Schema.string(description = "Rascunho de Apresentação da obra claramente baseado nas gravações"),
            "dedicationSuggestion" to Schema.string(description = "Sugestão editável de Dedicatória (ou orientação para o autor preencher)"),
            "acknowledgmentsSuggestion" to Schema.string(description = "Sugestão editável de Agradecimentos (não atribuir como já escrita pelo autor)"),
            "prefaceSuggestion" to Schema.string(description = "Sugestão editável de Prefácio alinhada ao tema"),
            "introduction" to Schema.string(description = "Introdução estruturada com base no conteúdo real das gravações"),
            "conclusion" to Schema.string(description = "Conclusão coerente com os argumentos das gravações"),
            "finalRemarks" to Schema.string(description = "Considerações finais"),
            "bibliography" to Schema.string(description = "Referências bibliográficas mencionadas nos áudios; se nenhuma foi citada, indicar explicitamente para o autor revisar"),
            "editorialNotes" to Schema.string(description = "Explicação transparente da divisão lógica proposta e pontos que dependem de confirmação do autor"),
            "chapters" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "partTitle" to Schema.string(description = "Nome da Parte do livro (ex: Parte I - Fundamentos), ou vazio se não houver partes"),
                        "chapterNumber" to Schema.integer(description = "Número sequencial do capítulo"),
                        "title" to Schema.string(description = "Título claro do capítulo"),
                        "subtitle" to Schema.string(description = "Subtítulo do capítulo"),
                        "summary" to Schema.string(description = "Síntese dos tópicos e argumentos que compõem este capítulo"),
                        "sourceRecordingNumbers" to Schema.array(
                            items = Schema.integer(),
                            description = "Índices (base 1) das gravações de origem que alimentam este capítulo"
                        )
                    )
                )
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelRegistry.EDITORIAL_REASONING_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = structureSchema
                temperature = 0.3f
            }
        )
    }

    suspend fun proposeStructure(
        book: BookProject,
        recordings: List<AudioRecording>
    ): ProposedBookStructure {
        val completed = recordings.filter { it.editorialTranscript.isNotBlank() || it.verbatimTranscript.isNotBlank() }
        require(completed.isNotEmpty()) {
            "Transcreva ao menos uma gravação antes de gerar a estrutura editorial."
        }

        val recordingsDigest = buildString {
            completed.forEachIndexed { idx, rec ->
                val text = rec.editorialTranscript.ifBlank { rec.verbatimTranscript }
                appendLine("=== GRAVAÇÃO ${idx + 1}: ${rec.title} (Idioma: ${rec.detectedLanguage}) ===")
                appendLine("Resumo: ${rec.contentSummary}")
                appendLine("Tópicos: ${rec.keyTopics.joinToString(", ")}")
                appendLine("Trecho principal:")
                appendLine(text.take(12000))
                appendLine()
            }
        }

        val prompt = """
            Você é o editor-chefe da LIVRO IA. Analise em conjunto as gravações abaixo para propor a arquitetura lógica de um livro completo.

            Título provisório: "${book.title}"
            Assunto: "${book.subject}"
            Idioma alvo: ${if (book.targetLanguage == "original") "Manter idioma original" else "Português Brasileiro"}

            REGRAS OBRIGATÓRIAS:
            1. Considere a continuidade temática entre as gravações. Não misture assuntos desconexos sem justificativa.
            2. A quantidade de capítulos deve depender exclusivamente da densidade e mudanças reais de assunto (não force um número fixo e evite capítulos muito curtos ou repetitivos).
            3. NÃO apresente dedicatória, agradecimentos ou prefácio como se já tivessem sido escritos pelo autor: deixe claro no texto que são sugestões/modelos editáveis com campos entre colchetes quando faltarem nomes ou fatos.
            4. NÃO invente referências bibliográficas que não foram mencionadas nas gravações.
            5. Cada capítulo deve indicar quais gravações (1..${completed.size}) o originam em sourceRecordingNumbers.

            CONTEÚDO DAS GRAVAÇÕES:
            $recordingsDigest
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("Estrutura vazia retornada pelo modelo.")
            val json = JSONObject(raw)
            val chaptersArray = json.optJSONArray("chapters")
            val chapters = if (chaptersArray != null) {
                (0 until chaptersArray.length()).map { i ->
                    val obj = chaptersArray.getJSONObject(i)
                    val srcArr = obj.optJSONArray("sourceRecordingNumbers")
                    val srcList = if (srcArr != null) {
                        (0 until srcArr.length()).map { srcArr.optInt(it, 1) }
                    } else listOf(1)
                    ProposedChapterOutline(
                        partTitle = obj.optString("partTitle", ""),
                        chapterNumber = obj.optInt("chapterNumber", i + 1),
                        title = obj.optString("title", "Capítulo ${i + 1}"),
                        subtitle = obj.optString("subtitle", ""),
                        summary = obj.optString("summary", ""),
                        sourceRecordingNumbers = srcList
                    )
                }
            } else emptyList()

            ProposedBookStructure(
                title = json.optString("title", book.title),
                subtitle = json.optString("subtitle", book.subtitle),
                presentation = json.optString("presentation", ""),
                dedicationSuggestion = json.optString("dedicationSuggestion", ""),
                acknowledgmentsSuggestion = json.optString("acknowledgmentsSuggestion", ""),
                prefaceSuggestion = json.optString("prefaceSuggestion", ""),
                introduction = json.optString("introduction", ""),
                conclusion = json.optString("conclusion", ""),
                finalRemarks = json.optString("finalRemarks", ""),
                bibliography = json.optString("bibliography", ""),
                editorialNotes = json.optString("editorialNotes", ""),
                chapters = chapters
            )
        } catch (e: CancellationException) {
            throw e
        }
    }
}

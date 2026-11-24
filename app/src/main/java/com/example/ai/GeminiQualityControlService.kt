package com.example.ai

import com.example.data.BookRepository
import com.example.model.BookChapter
import com.example.model.BookProject
import com.example.model.QcIssueItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class QualityAuditResult(
    val summary: String,
    val readyWithCaveats: Boolean,
    val issues: List<QcIssueItem>
)

class GeminiQualityControlService {

    private val qcSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Parecer editorial objetivo indicando explicitamente os pontos que ainda precisam de conferência humana"),
            "issues" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "severity" to Schema.enumeration(listOf("ERROR", "WARNING", "INFO")),
                        "category" to Schema.string(description = "Ex: ORTOGRAFIA, GRAMATICA, REPETICAO, PARAGRAFO_INCOMPLETO, DUPLICIDADE, INCONSISTENCIA_TITULO, SEQUENCIA, REFERENCIA, TRECHO_REVISAO"),
                        "location" to Schema.string(description = "Onde ocorre (ex: Capítulo 2, Pré-textuais, Referências)"),
                        "message" to Schema.string(description = "Descrição clara e acionável do ponto encontrado")
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
                responseSchema = qcSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun runQualityAudit(
        book: BookProject,
        chapters: List<BookChapter>
    ): QualityAuditResult {
        // 1. Deterministic structural checks first
        val deterministicIssues = mutableListOf<QcIssueItem>()

        if (book.authorName.isBlank()) {
            deterministicIssues.add(
                QcIssueItem(
                    severity = "WARNING",
                    category = "PRE_TEXTUAIS",
                    location = "Capa / Ficha",
                    message = "Nome do autor ainda não foi preenchido.",
                    chapterId = ""
                )
            )
        }
        if (book.dedication.contains("[") || book.acknowledgments.contains("[") || book.preface.contains("[")) {
            deterministicIssues.add(
                QcIssueItem(
                    severity = "WARNING",
                    category = "PRE_TEXTUAIS",
                    location = "Elementos Pré-Textuais",
                    message = "Há marcadores entre colchetes [...] nos elementos pré-textuais aguardando personalização do autor.",
                    chapterId = ""
                )
            )
        }
        if (chapters.isEmpty()) {
            deterministicIssues.add(
                QcIssueItem(
                    severity = "ERROR",
                    category = "ESTRUTURA",
                    location = "Manuscrito",
                    message = "O livro ainda não possui capítulos cadastrados.",
                    chapterId = ""
                )
            )
        }

        // Check duplicate chapter titles, sequence gaps, short chapters, and [REVISAR] markers
        val seenTitles = mutableSetOf<String>()
        chapters.forEachIndexed { idx, chap ->
            val normTitle = chap.title.trim().lowercase()
            if (!seenTitles.add(normTitle)) {
                deterministicIssues.add(
                    QcIssueItem(
                        severity = "ERROR",
                        category = "DUPLICIDADE",
                        location = "Capítulo ${chap.chapterNumber}",
                        message = "Título duplicado detectado: \"${chap.title}\".",
                        chapterId = chap.id
                    )
                )
            }
            if (chap.chapterNumber != (idx + 1).toLong()) {
                deterministicIssues.add(
                    QcIssueItem(
                        severity = "WARNING",
                        category = "SEQUENCIA",
                        location = "Capítulo ${chap.chapterNumber}",
                        message = "Numeração fora de ordem sequencial (esperado ${idx + 1}).",
                        chapterId = chap.id
                    )
                )
            }
            val markerCount = BookRepository.countReviewMarkers(chap.content)
            if (markerCount > 0) {
                deterministicIssues.add(
                    QcIssueItem(
                        severity = "ERROR",
                        category = "TRECHO_REVISAO",
                        location = "Capítulo ${chap.chapterNumber}: ${chap.title}",
                        message = "$markerCount trecho(s) marcado(s) com [REVISAR] exigem conferência humana antes da publicação.",
                        chapterId = chap.id
                    )
                )
            }
            if (!chap.approved) {
                deterministicIssues.add(
                    QcIssueItem(
                        severity = "INFO",
                        category = "APROVACAO",
                        location = "Capítulo ${chap.chapterNumber}: ${chap.title}",
                        message = "Capítulo ainda não marcado como aprovado pelo autor.",
                        chapterId = chap.id
                    )
                )
            }
        }

        // 2. Gemini editorial deep QA
        val manuscriptDigest = buildString {
            appendLine("Título: ${book.title}")
            appendLine("Subtítulo: ${book.subtitle}")
            appendLine("Introdução: ${book.introduction.take(2000)}")
            appendLine("Conclusão: ${book.conclusion.take(2000)}")
            appendLine("Referências: ${book.bibliography.take(1500)}")
            chapters.forEach { chap ->
                appendLine("=== Capítulo ${chap.chapterNumber}: ${chap.title} (${chap.subtitle}) ===")
                appendLine(chap.content.take(4000))
                appendLine()
            }
        }

        val prompt = """
            Você é o revisor final de controle de qualidade da LIVRO IA.
            Inspecione o manuscrito abaixo buscando:
            - Erros ortográficos ou gramaticais.
            - Repetições excessivas de palavras ou ideias entre capítulos.
            - Parágrafos incompletos ou frases truncadas.
            - Inconsistências de títulos/subtítulos ou saltos na sequência lógica.
            - Referências bibliográficas incompletas.
            - Trechos marcados como [REVISAR] ou que careçam de conferência humana.

            REGRA CRÍTICA: Nunca declare que o livro está 100% pronto sem indicar explicitamente no resumo os pontos que exigem conferência do autor.

            MANUSCRITO:
            $manuscriptDigest
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val raw = response.text ?: "{}"
            val json = JSONObject(raw)
            val summary = json.optString(
                "summary",
                "Revisão concluída. Confira os apontamentos listados antes de publicar."
            )
            val arr = json.optJSONArray("issues")
            val aiIssues = if (arr != null) {
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val location = obj.optString("location", "Manuscrito")
                    val matchedChap = chapters.firstOrNull {
                        location.contains("Capítulo ${it.chapterNumber}", ignoreCase = true) ||
                            location.contains(it.title, ignoreCase = true)
                    }
                    QcIssueItem(
                        severity = obj.optString("severity", "WARNING"),
                        category = obj.optString("category", "REVISAO"),
                        location = location,
                        message = obj.optString("message", ""),
                        chapterId = matchedChap?.id ?: ""
                    )
                }.filter { it.message.isNotBlank() }
            } else emptyList()

            val combined = (deterministicIssues + aiIssues).distinctBy { "${it.location}:${it.message}" }
            QualityAuditResult(
                summary = summary,
                readyWithCaveats = true,
                issues = combined
            )
        } catch (e: CancellationException) {
            throw e
        }
    }
}

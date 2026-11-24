package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class RecordingStatus(val labelPt: String) {
    PENDING("Aguardando"),
    PROCESSING("Processando"),
    COMPLETED("Concluído"),
    ERROR("Erro");

    companion object {
        fun fromRaw(raw: String?): RecordingStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class PageSizePreset(val code: String, val labelPt: String, val widthCm: Float, val heightCm: Float) {
    A5("A5", "A5 (14,8 × 21 cm)", 14.8f, 21.0f),
    BOOK_16X23("16x23", "Livro Padrão (16 × 23 cm)", 16.0f, 23.0f),
    CUSTOM("CUSTOM", "Personalizado", 15.0f, 22.0f);

    companion object {
        fun fromCode(code: String?): PageSizePreset =
            entries.firstOrNull { it.code == code } ?: A5
    }
}

enum class BookFontPreset(val code: String, val labelPt: String, val cssFont: String) {
    MERRIWEATHER("MERRIWEATHER", "Merriweather (Serif Clássica)", "Georgia, 'Times New Roman', serif"),
    PLAYFAIR_DISPLAY("PLAYFAIR_DISPLAY", "Playfair Display (Editorial)", "'Palatino Linotype', Georgia, serif"),
    PLUS_JAKARTA_SANS("PLUS_JAKARTA_SANS", "Plus Jakarta Sans (Moderna)", "Arial, Helvetica, sans-serif");

    companion object {
        fun fromCode(code: String?): BookFontPreset =
            entries.firstOrNull { it.code == code } ?: MERRIWEATHER
    }
}

enum class ChapterAiAction(val labelPt: String, val instructionPt: String) {
    FIX_GRAMMAR("Corrigir gramática", "Corrija ortografia, pontuação, crase e concordância verbal/nominal em português brasileiro sem alterar o estilo nem inventar fatos."),
    IMPROVE_WRITING("Melhorar escrita", "Aprimore fluidez, coesão e elegância literária mantendo integralmente o sentido, voz e ideias do autor."),
    MORE_FORMAL("Tornar mais formal", "Adapte o trecho para um registro culto e formal adequado a publicação editorial, sem perder a clareza nem acrescentar ideias novas."),
    SIMPLER("Tornar mais simples", "Reescreva o trecho com vocabulário mais acessível, frases diretas e leitura fluida, preservando todo o conteúdo."),
    INCREASE_CLARITY("Aumentar clareza", "Reestruture o trecho para eliminar ambiguidades e tornar o raciocínio cristalino, mantendo a fidelidade ao original."),
    DEVELOP_IDEA("Desenvolver ideia", "Desenvolva melhor a explicação e as transições do argumento já presente no texto, SEM inventar fatos, dados, histórias ou citações externas."),
    SUMMARIZE("Resumir", "Condense o trecho preservando as ideias centrais e expressões-chave do autor."),
    TRANSLATE_PT_BR("Traduzir (PT-BR)", "Traduza fielmente para o português brasileiro natural, preservando nomes próprios, termos técnicos e citações bíblicas ou literárias.")
}

enum class ExportFormat(val extension: String, val mimeType: String, val labelPt: String) {
    PDF("pdf", "application/pdf", "PDF Diagramado"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "DOCX (Microsoft Word)"),
    EPUB("epub", "application/epub+zip", "EPUB (E-book)")
}

enum class WorkspaceTab(val titlePt: String, val shortPt: String) {
    LIBRARY("Biblioteca", "Livros"),
    AUDIO("Estúdio de Áudio", "Áudio"),
    STRUCTURE("Estrutura & Pré-Textuais", "Estrutura"),
    EDITOR("Editor de Manuscrito", "Editor"),
    QUALITY("Controle de Qualidade", "Revisão"),
    EXPORT("Diagramação & Exportação", "Exportar")
}

@Keep
@IgnoreExtraProperties
data class BookProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val authorName: String = "",
    val subject: String = "",
    val currentStep: Long = 1L,
    val structureApproved: Boolean = false,
    val targetLanguage: String = "pt-BR",
    val presentation: String = "",
    val dedication: String = "",
    val acknowledgments: String = "",
    val preface: String = "",
    val introduction: String = "",
    val conclusion: String = "",
    val finalRemarks: String = "",
    val bibliography: String = "",
    val aiGeneratedOutlineNotes: String = "",
    val layoutPageSize: String = "A5",
    val layoutCustomWidthCm: Double = 15.0,
    val layoutCustomHeightCm: Double = 22.0,
    val layoutMarginInnerMm: Long = 20L,
    val layoutMarginOuterMm: Long = 15L,
    val layoutFontFamily: String = "MERRIWEATHER",
    val layoutFontSizePt: Long = 11L,
    val layoutLineSpacing: Double = 1.5,
    val layoutParagraphIndentMm: Long = 8L,
    val layoutJustified: Boolean = true,
    val layoutShowHeaders: Boolean = true,
    val layoutShowPageNumbers: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> {
        return mapOf(
            "userId" to ownerUid,
            "title" to title.trim().take(200),
            "subtitle" to subtitle.trim().take(300),
            "authorName" to authorName.trim().take(120),
            "subject" to subject.trim().take(1000),
            "currentStep" to currentStep.coerceIn(1L, 12L),
            "structureApproved" to structureApproved,
            "targetLanguage" to if (targetLanguage == "original") "original" else "pt-BR",
            "presentation" to presentation.take(20000),
            "dedication" to dedication.take(5000),
            "acknowledgments" to acknowledgments.take(10000),
            "preface" to preface.take(20000),
            "introduction" to introduction.take(30000),
            "conclusion" to conclusion.take(30000),
            "finalRemarks" to finalRemarks.take(20000),
            "bibliography" to bibliography.take(20000),
            "aiGeneratedOutlineNotes" to aiGeneratedOutlineNotes.take(10000),
            "layoutPageSize" to PageSizePreset.fromCode(layoutPageSize).code,
            "layoutCustomWidthCm" to layoutCustomWidthCm.coerceIn(10.0, 30.0),
            "layoutCustomHeightCm" to layoutCustomHeightCm.coerceIn(14.0, 40.0),
            "layoutMarginInnerMm" to layoutMarginInnerMm.coerceIn(5L, 50L),
            "layoutMarginOuterMm" to layoutMarginOuterMm.coerceIn(5L, 50L),
            "layoutFontFamily" to BookFontPreset.fromCode(layoutFontFamily).code,
            "layoutFontSizePt" to layoutFontSizePt.coerceIn(8L, 18L),
            "layoutLineSpacing" to layoutLineSpacing.coerceIn(1.0, 2.5),
            "layoutParagraphIndentMm" to layoutParagraphIndentMm.coerceIn(0L, 20L),
            "layoutJustified" to layoutJustified,
            "layoutShowHeaders" to layoutShowHeaders,
            "layoutShowPageNumbers" to layoutShowPageNumbers,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@Keep
@IgnoreExtraProperties
data class AudioRecording(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val title: String = "",
    val fileName: String = "",
    val mimeType: String = "audio/mpeg",
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val orderIndex: Long = 0L,
    val localFilePath: String = "",
    val detectedLanguage: String = "",
    val translateToPtBr: Boolean = true,
    val verbatimTranscript: String = "",
    val editorialTranscript: String = "",
    val contentSummary: String = "",
    val keyTopics: List<String> = emptyList(),
    val status: String = RecordingStatus.PENDING.name,
    val errorMessage: String = "",
    val chunksCompleted: Long = 0L,
    val totalChunks: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: RecordingStatus get() = RecordingStatus.fromRaw(status)

    fun toCreateMap(ownerUid: String, targetBookId: String): Map<String, Any> {
        return mapOf(
            "userId" to ownerUid,
            "bookId" to targetBookId,
            "title" to title.trim().ifBlank { fileName }.take(200),
            "fileName" to fileName.trim().ifBlank { "audio.m4a" }.take(255),
            "mimeType" to mimeType.trim().ifBlank { "audio/mp4" }.take(100),
            "sizeBytes" to sizeBytes.coerceAtLeast(0L),
            "durationMs" to durationMs.coerceAtLeast(0L),
            "orderIndex" to orderIndex.coerceIn(0L, 500L),
            "localFilePath" to localFilePath.take(500),
            "detectedLanguage" to detectedLanguage.take(64),
            "translateToPtBr" to translateToPtBr,
            "verbatimTranscript" to verbatimTranscript.take(300000),
            "editorialTranscript" to editorialTranscript.take(300000),
            "contentSummary" to contentSummary.take(5000),
            "keyTopics" to keyTopics.take(25),
            "status" to RecordingStatus.fromRaw(status).name,
            "errorMessage" to errorMessage.take(1000),
            "chunksCompleted" to chunksCompleted.coerceAtLeast(0L),
            "totalChunks" to totalChunks.coerceAtLeast(1L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@Keep
@IgnoreExtraProperties
data class BookChapter(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val partTitle: String = "",
    val chapterNumber: Long = 1L,
    val title: String = "",
    val subtitle: String = "",
    val content: String = "",
    val summary: String = "",
    val orderIndex: Long = 0L,
    val approved: Boolean = false,
    val needsReviewCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val wordCount: Int
        get() = content.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    fun toCreateMap(ownerUid: String, targetBookId: String): Map<String, Any> {
        return mapOf(
            "userId" to ownerUid,
            "bookId" to targetBookId,
            "partTitle" to partTitle.take(200),
            "chapterNumber" to chapterNumber.coerceIn(1L, 500L),
            "title" to title.trim().ifBlank { "Capítulo $chapterNumber" }.take(250),
            "subtitle" to subtitle.take(300),
            "content" to content.take(300000),
            "summary" to summary.take(5000),
            "orderIndex" to orderIndex.coerceIn(0L, 500L),
            "approved" to approved,
            "needsReviewCount" to needsReviewCount.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@Keep
@IgnoreExtraProperties
data class BookVersion(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val label: String = "",
    val note: String = "",
    val bookTitleSnapshot: String = "",
    val bookSubtitleSnapshot: String = "",
    val chapterCount: Long = 0L,
    val wordCount: Long = 0L,
    val chaptersSnapshot: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class QcIssueItem(
    val severity: String, // "ERROR" | "WARNING" | "INFO"
    val category: String,
    val location: String,
    val message: String,
    val chapterId: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "severity" to severity,
        "category" to category,
        "location" to location,
        "message" to message,
        "chapterId" to chapterId
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): QcIssueItem = QcIssueItem(
            severity = map["severity"] as? String ?: "WARNING",
            category = map["category"] as? String ?: "REVISAO",
            location = map["location"] as? String ?: "",
            message = map["message"] as? String ?: "",
            chapterId = map["chapterId"] as? String ?: ""
        )
    }
}

@Keep
@IgnoreExtraProperties
data class QcReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val summary: String = "",
    val readyWithCaveats: Boolean = true,
    val issueCount: Long = 0L,
    val issues: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun parsedIssues(): List<QcIssueItem> = issues.map { QcIssueItem.fromMap(it) }
}

package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AudioChunker
import com.example.ai.GeminiLiteraryEditorService
import com.example.ai.GeminiQualityControlService
import com.example.ai.GeminiStructureService
import com.example.ai.GeminiTranscriptionService
import com.example.ai.ProposedBookStructure
import com.example.ai.ProposedChapterOutline
import com.example.data.BookRepository
import com.example.export.BookExportService
import com.example.export.ExportedFileResult
import com.example.model.AudioRecording
import com.example.model.BookChapter
import com.example.model.BookProject
import com.example.model.BookVersion
import com.example.model.ChapterAiAction
import com.example.model.ExportFormat
import com.example.model.QcReport
import com.example.model.RecordingStatus
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class PendingAiSuggestion(
    val chapterId: String,
    val originalText: String,
    val suggestedText: String,
    val action: ChapterAiAction,
    val replaceStart: Int,
    val replaceEnd: Int
)

class BookWorkspaceViewModel(
    private val repository: BookRepository,
    private val currentUserId: String,
    private val bookId: String,
    private val transcriptionService: GeminiTranscriptionService = GeminiTranscriptionService(),
    private val structureService: GeminiStructureService = GeminiStructureService(),
    private val literaryEditorService: GeminiLiteraryEditorService = GeminiLiteraryEditorService(),
    private val qcService: GeminiQualityControlService = GeminiQualityControlService(),
    private val exportService: BookExportService = BookExportService()
) : ViewModel() {

    val bookState: StateFlow<UiState<BookProject?>> = repository.observeBook(currentUserId, bookId)
        .map<BookProject?, UiState<BookProject?>> { UiState.Success(it) }
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(UiState.Error((fs ?: error).localizedMessage ?: "Erro ao carregar projeto."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val recordingsState: StateFlow<List<AudioRecording>> = repository.observeRecordings(currentUserId, bookId)
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing recordings", fs ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val chaptersState: StateFlow<List<BookChapter>> = repository.observeChapters(currentUserId, bookId)
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing chapters", fs ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val versionsState: StateFlow<List<BookVersion>> = repository.observeVersions(currentUserId, bookId)
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val latestQcState: StateFlow<QcReport?> = repository.observeLatestQcReport(currentUserId, bookId)
        .catch { error ->
            val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && error is CancellationException) throw error
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _proposedStructure = MutableStateFlow<ProposedBookStructure?>(null)
    val proposedStructure: StateFlow<ProposedBookStructure?> = _proposedStructure.asStateFlow()

    private val _isAnalyzingStructure = MutableStateFlow(false)
    val isAnalyzingStructure: StateFlow<Boolean> = _isAnalyzingStructure.asStateFlow()

    private val _manuscriptGenerationProgress = MutableStateFlow<String?>(null)
    val manuscriptGenerationProgress: StateFlow<String?> = _manuscriptGenerationProgress.asStateFlow()

    private val _streamingChapterPreview = MutableStateFlow("")
    val streamingChapterPreview: StateFlow<String> = _streamingChapterPreview.asStateFlow()

    private val _isRunningRewrite = MutableStateFlow(false)
    val isRunningRewrite: StateFlow<Boolean> = _isRunningRewrite.asStateFlow()

    private val _pendingSuggestion = MutableStateFlow<PendingAiSuggestion?>(null)
    val pendingSuggestion: StateFlow<PendingAiSuggestion?> = _pendingSuggestion.asStateFlow()

    private val _isRunningQc = MutableStateFlow(false)
    val isRunningQc: StateFlow<Boolean> = _isRunningQc.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _lastExportedFile = MutableStateFlow<ExportedFileResult?>(null)
    val lastExportedFile: StateFlow<ExportedFileResult?> = _lastExportedFile.asStateFlow()

    private val _autoSaveStatus = MutableStateFlow("Salvo na nuvem")
    val autoSaveStatus: StateFlow<String> = _autoSaveStatus.asStateFlow()

    private val _undoStack = MutableStateFlow<List<BookChapter>>(emptyList())
    val canUndo: StateFlow<Boolean> = _undoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private var autoSaveJob: Job? = null

    private fun currentBook(): BookProject? = (bookState.value as? UiState.Success)?.data

    // =========================================================================
    // AUDIO INGESTION & CHUNKED TRANSCRIPTION
    // =========================================================================
    fun importAudioFiles(context: Context, uris: List<Uri>, translateToPtBr: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val appCtx = context.applicationContext
            val existingCount = recordingsState.value.size
            var addedCount = 0
            val createdIds = mutableListOf<Pair<String, AudioRecording>>()

            uris.forEachIndexed { idx, uri ->
                runCatching {
                    val prepared = AudioChunker.copyUriToPrivateStorage(appCtx, bookId, uri)
                    val title = prepared.fileName.substringBeforeLast('.')
                        .ifBlank { "Gravação ${existingCount + idx + 1}" }
                    val rec = AudioRecording(
                        userId = currentUserId,
                        bookId = bookId,
                        title = title,
                        fileName = prepared.fileName,
                        mimeType = prepared.mimeType,
                        sizeBytes = prepared.sizeBytes,
                        durationMs = prepared.durationMs,
                        orderIndex = (existingCount + idx).toLong(),
                        localFilePath = prepared.localFile.absolutePath,
                        translateToPtBr = translateToPtBr,
                        status = RecordingStatus.PENDING.name,
                        chunksCompleted = 0L,
                        totalChunks = 1L
                    )
                    val id = repository.addRecording(bookId, rec).getOrThrow()
                    createdIds.add(id to rec.copy(id = id))
                    addedCount++
                }.onFailure { err ->
                    _messages.emit("Erro ao importar áudio: ${err.localizedMessage ?: "arquivo inválido"}")
                }
            }

            if (addedCount > 0) {
                repository.updateBookMetadata(bookId, mapOf("currentStep" to 3L))
                _messages.emit("$addedCount gravação(ões) adicionada(s). Iniciando transcrição...")
                createdIds.forEach { (_, rec) ->
                    transcribeRecordingInternal(appCtx, rec)
                }
            }
        }
    }

    fun addRecordedAudio(context: Context, file: File, durationMs: Long, title: String, translateToPtBr: Boolean) {
        viewModelScope.launch {
            val existingCount = recordingsState.value.size
            val rec = AudioRecording(
                userId = currentUserId,
                bookId = bookId,
                title = title.ifBlank { "Gravação de Voz ${existingCount + 1}" },
                fileName = file.name,
                mimeType = "audio/mp4",
                sizeBytes = file.length(),
                durationMs = durationMs,
                orderIndex = existingCount.toLong(),
                localFilePath = file.absolutePath,
                translateToPtBr = translateToPtBr,
                status = RecordingStatus.PENDING.name,
                chunksCompleted = 0L,
                totalChunks = 1L
            )
            repository.addRecording(bookId, rec).onSuccess { newId ->
                repository.updateBookMetadata(bookId, mapOf("currentStep" to 3L))
                transcribeRecordingInternal(context.applicationContext, rec.copy(id = newId))
            }.onFailure { err ->
                _messages.emit(err.localizedMessage ?: "Erro ao salvar gravação.")
            }
        }
    }

    fun reprocessRecording(context: Context, recording: AudioRecording) {
        viewModelScope.launch {
            transcribeRecordingInternal(context.applicationContext, recording)
        }
    }

    private suspend fun transcribeRecordingInternal(context: Context, recording: AudioRecording) {
        val book = currentBook()
        val file = File(recording.localFilePath)
        if (!file.exists()) {
            repository.updateRecordingProgress(
                bookId,
                recording.id,
                mapOf(
                    "status" to RecordingStatus.ERROR.name,
                    "errorMessage" to "Arquivo de áudio local não encontrado neste dispositivo."
                )
            )
            return
        }

        try {
            val chunks = AudioChunker.splitIntoChunks(context, file, recording.mimeType)
            repository.updateRecordingProgress(
                bookId,
                recording.id,
                mapOf(
                    "status" to RecordingStatus.PROCESSING.name,
                    "errorMessage" to "",
                    "chunksCompleted" to 0L,
                    "totalChunks" to chunks.size.toLong()
                )
            )

            val verbatimParts = StringBuilder()
            val editorialParts = StringBuilder()
            val summaryParts = StringBuilder()
            val allTopics = linkedSetOf<String>()
            var detectedLang = ""

            for (chunk in chunks) {
                val res = transcriptionService.transcribeChunk(
                    chunk = chunk,
                    recordingTitle = recording.title,
                    bookSubject = book?.subject ?: "",
                    translateToPtBr = recording.translateToPtBr
                )
                if (detectedLang.isBlank()) detectedLang = res.detectedLanguage
                if (res.verbatimTranscript.isNotBlank()) {
                    if (verbatimParts.isNotEmpty()) verbatimParts.append("\n\n")
                    verbatimParts.append(res.verbatimTranscript.trim())
                }
                if (res.editorialTranscript.isNotBlank()) {
                    if (editorialParts.isNotEmpty()) editorialParts.append("\n\n")
                    editorialParts.append(res.editorialTranscript.trim())
                }
                if (res.contentSummary.isNotBlank()) {
                    if (summaryParts.isNotEmpty()) summaryParts.append(" ")
                    summaryParts.append(res.contentSummary.trim())
                }
                allTopics.addAll(res.keyTopics)

                repository.updateRecordingProgress(
                    bookId,
                    recording.id,
                    mapOf(
                        "chunksCompleted" to (chunk.index + 1).toLong(),
                        "detectedLanguage" to detectedLang.take(64),
                        "verbatimTranscript" to verbatimParts.toString().take(300000),
                        "editorialTranscript" to editorialParts.toString().take(300000),
                        "contentSummary" to summaryParts.toString().take(5000),
                        "keyTopics" to allTopics.toList().take(25)
                    )
                )
            }

            repository.updateRecordingProgress(
                bookId,
                recording.id,
                mapOf(
                    "status" to RecordingStatus.COMPLETED.name,
                    "errorMessage" to ""
                )
            )
            repository.updateBookMetadata(bookId, mapOf("currentStep" to 5L))
            _messages.emit("Transcrição concluída: ${recording.title}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed for ${recording.id}", e)
            repository.updateRecordingProgress(
                bookId,
                recording.id,
                mapOf(
                    "status" to RecordingStatus.ERROR.name,
                    "errorMessage" to (e.localizedMessage ?: "Falha na transcrição com Gemini.")
                )
            )
            _messages.emit("Erro ao transcrever ${recording.title}: ${e.localizedMessage ?: "tente reprocessar"}")
        }
    }

    fun moveRecording(index: Int, delta: Int) {
        val list = recordingsState.value.toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        viewModelScope.launch {
            repository.reorderRecordings(bookId, list)
        }
    }

    fun toggleRecordingTranslation(recording: AudioRecording) {
        viewModelScope.launch {
            repository.updateRecordingProgress(
                bookId,
                recording.id,
                mapOf("translateToPtBr" to !recording.translateToPtBr)
            )
        }
    }

    fun renameRecording(recording: AudioRecording, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.updateRecordingProgress(
                bookId,
                recording.id,
                mapOf("title" to newTitle.trim().take(200))
            )
        }
    }

    fun deleteRecording(recording: AudioRecording) {
        viewModelScope.launch {
            repository.deleteRecording(bookId, recording)
        }
    }

    // =========================================================================
    // STRUCTURE ANALYSIS & CHAPTER GENERATION
    // =========================================================================
    fun proposeBookStructure() {
        val book = currentBook() ?: return
        val recs = recordingsState.value
        viewModelScope.launch {
            _isAnalyzingStructure.value = true
            try {
                val proposal = structureService.proposeStructure(book, recs)
                _proposedStructure.value = proposal
                repository.updateBookMetadata(
                    bookId,
                    mapOf(
                        "title" to proposal.title.take(200),
                        "subtitle" to proposal.subtitle.take(300),
                        "presentation" to proposal.presentation.take(20000),
                        "dedication" to proposal.dedicationSuggestion.take(5000),
                        "acknowledgments" to proposal.acknowledgmentsSuggestion.take(10000),
                        "preface" to proposal.prefaceSuggestion.take(20000),
                        "introduction" to proposal.introduction.take(30000),
                        "conclusion" to proposal.conclusion.take(30000),
                        "finalRemarks" to proposal.finalRemarks.take(20000),
                        "bibliography" to proposal.bibliography.take(20000),
                        "aiGeneratedOutlineNotes" to proposal.editorialNotes.take(10000),
                        "currentStep" to 7L
                    )
                )
                _messages.emit("Estrutura editorial proposta com ${proposal.chapters.size} capítulos. Revise antes de gerar o manuscrito.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Structure analysis failed", e)
                _messages.emit("Falha ao analisar estrutura: ${e.localizedMessage ?: "tente novamente"}")
            } finally {
                _isAnalyzingStructure.value = false
            }
        }
    }

    fun updateProposedOutlineChapters(updated: List<ProposedChapterOutline>) {
        val current = _proposedStructure.value ?: return
        _proposedStructure.value = current.copy(chapters = updated)
    }

    fun approveStructureAndGenerateManuscript() {
        val book = currentBook() ?: return
        val recs = recordingsState.value.filter { it.editorialTranscript.isNotBlank() || it.verbatimTranscript.isNotBlank() }
        val outlines = _proposedStructure.value?.chapters ?: run {
            if (recs.isEmpty()) emptyList()
            else recs.mapIndexed { idx, rec ->
                ProposedChapterOutline(
                    partTitle = "",
                    chapterNumber = idx + 1,
                    title = rec.title,
                    subtitle = "",
                    summary = rec.contentSummary,
                    sourceRecordingNumbers = listOf(idx + 1)
                )
            }
        }
        if (outlines.isEmpty()) {
            viewModelScope.launch { _messages.emit("Transcreva pelo menos uma gravação primeiro.") }
            return
        }

        viewModelScope.launch {
            try {
                repository.updateBookMetadata(
                    bookId,
                    mapOf("structureApproved" to true, "currentStep" to 9L)
                )

                val generatedChapters = mutableListOf<BookChapter>()
                outlines.forEachIndexed { idx, outline ->
                    _manuscriptGenerationProgress.value =
                        "Redigindo capítulo ${idx + 1} de ${outlines.size}: ${outline.title}"
                    _streamingChapterPreview.value = ""

                    var finalContent = ""
                    literaryEditorService.generateChapterStream(book, outline, recs)
                        .collect { partial ->
                            finalContent = partial
                            _streamingChapterPreview.value = partial
                        }

                    generatedChapters.add(
                        BookChapter(
                            userId = currentUserId,
                            bookId = bookId,
                            partTitle = outline.partTitle.take(200),
                            chapterNumber = (idx + 1).toLong(),
                            title = outline.title.take(250),
                            subtitle = outline.subtitle.take(300),
                            content = finalContent.take(300000),
                            summary = outline.summary.take(5000),
                            orderIndex = idx.toLong(),
                            approved = false,
                            needsReviewCount = BookRepository.countReviewMarkers(finalContent).toLong()
                        )
                    )
                }

                repository.replaceBookChapters(bookId, generatedChapters).getOrThrow()
                repository.saveVersionSnapshot(
                    book = book,
                    chapters = generatedChapters,
                    label = "Geração Inicial IA",
                    note = "Manuscrito inicial organizado a partir das gravações."
                )
                repository.updateBookMetadata(bookId, mapOf("currentStep" to 10L))
                _messages.emit("Manuscrito completo gerado com ${generatedChapters.size} capítulos!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chapter generation failed", e)
                _messages.emit("Erro ao gerar manuscrito: ${e.localizedMessage ?: "tente novamente"}")
            } finally {
                _manuscriptGenerationProgress.value = null
                _streamingChapterPreview.value = ""
            }
        }
    }

    fun savePrePostTextualFields(updates: Map<String, Any>) {
        viewModelScope.launch {
            _autoSaveStatus.value = "Salvando..."
            val res = repository.updateBookMetadata(bookId, updates)
            _autoSaveStatus.value = if (res.isSuccess) "Salvo na nuvem" else "Erro ao salvar"
        }
    }

    // =========================================================================
    // INTERNAL BOOK EDITOR (AUTO-SAVE, UNDO, SPLIT, MERGE, REORDER, AI REWRITE)
    // =========================================================================
    fun updateChapterWithAutoSave(previous: BookChapter, updated: BookChapter, recordUndo: Boolean = true) {
        if (recordUndo && previous != updated) {
            _undoStack.value = (_undoStack.value + previous).takeLast(20)
        }
        _autoSaveStatus.value = "Alterações pendentes..."
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(600L)
            _autoSaveStatus.value = "Salvando..."
            val res = repository.saveChapter(bookId, updated)
            _autoSaveStatus.value = if (res.isSuccess) "Salvo automaticamente" else "Falha ao salvar"
        }
    }

    fun undoLastChapterEdit() {
        val stack = _undoStack.value
        if (stack.isEmpty()) return
        val last = stack.last()
        _undoStack.value = stack.dropLast(1)
        viewModelScope.launch {
            repository.saveChapter(bookId, last)
            _messages.emit("Última alteração desfeita.")
        }
    }

    fun createNewChapter(title: String) {
        val current = chaptersState.value
        val nextNum = (current.size + 1).toLong()
        viewModelScope.launch {
            val newChap = BookChapter(
                userId = currentUserId,
                bookId = bookId,
                chapterNumber = nextNum,
                title = title.trim().ifBlank { "Capítulo $nextNum" },
                content = "",
                orderIndex = current.size.toLong(),
                approved = false
            )
            repository.saveChapter(bookId, newChap)
                .onSuccess { _messages.emit("Capítulo \"$title\" criado.") }
        }
    }

    fun deleteChapter(chapter: BookChapter) {
        viewModelScope.launch {
            repository.deleteChapter(bookId, chapter.id, chaptersState.value)
                .onSuccess { _messages.emit("Capítulo excluído.") }
        }
    }

    fun moveChapter(fromIndex: Int, toIndex: Int) {
        val list = chaptersState.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        viewModelScope.launch {
            repository.reorderChapters(bookId, list)
        }
    }

    fun splitChapterAtParagraph(chapter: BookChapter, paragraphIndex: Int) {
        val paragraphs = chapter.content.split(Regex("\n\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.size < 2 || paragraphIndex <= 0 || paragraphIndex >= paragraphs.size) {
            viewModelScope.launch { _messages.emit("É necessário ter ao menos 2 parágrafos para dividir o capítulo.") }
            return
        }
        val firstPart = paragraphs.subList(0, paragraphIndex).joinToString("\n\n")
        val secondPart = paragraphs.subList(paragraphIndex, paragraphs.size).joinToString("\n\n")

        viewModelScope.launch {
            val currentList = chaptersState.value
            val idx = currentList.indexOfFirst { it.id == chapter.id }.takeIf { it >= 0 } ?: return@launch
            val updatedFirst = chapter.copy(content = firstPart)
            val newSecond = BookChapter(
                userId = currentUserId,
                bookId = bookId,
                partTitle = chapter.partTitle,
                chapterNumber = chapter.chapterNumber + 1,
                title = "${chapter.title} (Parte 2)",
                subtitle = chapter.subtitle,
                content = secondPart,
                orderIndex = chapter.orderIndex + 1,
                approved = false
            )
            val rebuilt = currentList.toMutableList()
            rebuilt[idx] = updatedFirst
            rebuilt.add(idx + 1, newSecond)
            repository.replaceBookChapters(bookId, rebuilt)
                .onSuccess { _messages.emit("Capítulo dividido em dois com sucesso.") }
        }
    }

    fun mergeChapterWithNext(chapter: BookChapter) {
        val list = chaptersState.value
        val idx = list.indexOfFirst { it.id == chapter.id }
        if (idx < 0 || idx >= list.lastIndex) {
            viewModelScope.launch { _messages.emit("Não há próximo capítulo para unir.") }
            return
        }
        val next = list[idx + 1]
        val mergedContent = buildString {
            append(chapter.content.trim())
            if (next.content.isNotBlank()) {
                append("\n\n## ${next.title}\n\n")
                append(next.content.trim())
            }
        }
        viewModelScope.launch {
            val updated = chapter.copy(content = mergedContent, approved = false)
            val rebuilt = list.toMutableList()
            rebuilt[idx] = updated
            rebuilt.removeAt(idx + 1)
            repository.replaceBookChapters(bookId, rebuilt)
                .onSuccess { _messages.emit("Capítulos unidos.") }
        }
    }

    fun requestAiRewrite(
        chapter: BookChapter,
        selectedText: String,
        replaceStart: Int,
        replaceEnd: Int,
        action: ChapterAiAction
    ) {
        val book = currentBook() ?: return
        val targetText = selectedText.ifBlank { chapter.content }
        if (targetText.isBlank()) {
            viewModelScope.launch { _messages.emit("O texto está vazio.") }
            return
        }
        val start = if (selectedText.isBlank()) 0 else replaceStart
        val end = if (selectedText.isBlank()) chapter.content.length else replaceEnd

        viewModelScope.launch {
            _isRunningRewrite.value = true
            try {
                val suggested = literaryEditorService.rewritePassage(
                    passage = targetText,
                    action = action,
                    bookTitle = book.title,
                    chapterTitle = chapter.title
                )
                _pendingSuggestion.value = PendingAiSuggestion(
                    chapterId = chapter.id,
                    originalText = targetText,
                    suggestedText = suggested,
                    action = action,
                    replaceStart = start,
                    replaceEnd = end
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit("Erro na revisão IA: ${e.localizedMessage ?: "tente novamente"}")
            } finally {
                _isRunningRewrite.value = false
            }
        }
    }

    fun acceptAiSuggestion(chapter: BookChapter) {
        val pending = _pendingSuggestion.value ?: return
        if (pending.chapterId != chapter.id) return
        val content = chapter.content
        val safeStart = pending.replaceStart.coerceIn(0, content.length)
        val safeEnd = pending.replaceEnd.coerceIn(safeStart, content.length)
        val newContent = content.substring(0, safeStart) + pending.suggestedText + content.substring(safeEnd)
        _pendingSuggestion.value = null
        updateChapterWithAutoSave(chapter, chapter.copy(content = newContent), recordUndo = true)
        viewModelScope.launch { _messages.emit("Alteração da IA aplicada ao capítulo.") }
    }

    fun rejectAiSuggestion() {
        _pendingSuggestion.value = null
    }

    fun saveVersion(label: String, note: String) {
        val book = currentBook() ?: return
        val chapters = chaptersState.value
        viewModelScope.launch {
            repository.saveVersionSnapshot(book, chapters, label, note)
                .onSuccess { _messages.emit("Versão \"$label\" salva no histórico.") }
                .onFailure { _messages.emit(it.localizedMessage ?: "Erro ao salvar versão.") }
        }
    }

    fun restoreVersion(version: BookVersion) {
        viewModelScope.launch {
            repository.restoreVersionSnapshot(bookId, version)
                .onSuccess { _messages.emit("Versão \"${version.label}\" restaurada.") }
                .onFailure { _messages.emit(it.localizedMessage ?: "Erro ao restaurar versão.") }
        }
    }

    // =========================================================================
    // QUALITY CONTROL & EXPORT
    // =========================================================================
    fun runQualityAudit() {
        val book = currentBook() ?: return
        val chapters = chaptersState.value
        viewModelScope.launch {
            _isRunningQc.value = true
            try {
                val result = qcService.runQualityAudit(book, chapters)
                repository.saveQcReport(
                    bookId = bookId,
                    summary = result.summary,
                    readyWithCaveats = result.readyWithCaveats,
                    issues = result.issues
                ).getOrThrow()
                repository.updateBookMetadata(bookId, mapOf("currentStep" to 11L))
                _messages.emit("Revisão de qualidade concluída: ${result.issues.size} apontamento(s).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit("Falha na revisão automática: ${e.localizedMessage ?: "tente novamente"}")
            } finally {
                _isRunningQc.value = false
            }
        }
    }

    fun updateLayoutConfig(updates: Map<String, Any>) {
        viewModelScope.launch {
            repository.updateBookMetadata(bookId, updates)
        }
    }

    fun exportBook(context: Context, format: ExportFormat) {
        val book = currentBook() ?: return
        val chapters = chaptersState.value
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val res = exportService.exportBook(context.applicationContext, book, chapters, format)
                _lastExportedFile.value = res
                repository.updateBookMetadata(bookId, mapOf("currentStep" to 12L))
                _messages.emit("${format.labelPt} gerado com sucesso!")
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _messages.emit("Falha ao exportar ${format.labelPt}: ${e.localizedMessage ?: "erro"}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun copyLastExportToUri(context: Context, destinationUri: Uri) {
        val exported = _lastExportedFile.value ?: return
        viewModelScope.launch {
            runCatching {
                exportService.copyFileToDestinationUri(context.applicationContext, exported.file, destinationUri)
            }.onSuccess {
                _messages.emit("Arquivo salvo no local escolhido!")
            }.onFailure { err ->
                _messages.emit("Falha ao salvar arquivo: ${err.localizedMessage}")
            }
        }
    }

    private companion object {
        const val TAG = "BookWorkspaceVM"
    }
}

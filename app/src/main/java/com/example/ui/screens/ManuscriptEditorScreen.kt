package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BookRepository
import com.example.model.BookChapter
import com.example.model.BookProject
import com.example.model.BookVersion
import com.example.model.ChapterAiAction
import com.example.ui.theme.Azure50
import com.example.ui.theme.Azure600
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.MerriweatherFamily
import com.example.ui.theme.Navy900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer
import com.example.viewmodel.PendingAiSuggestion
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ManuscriptEditorScreen(
    book: BookProject,
    chapters: List<BookChapter>,
    versions: List<BookVersion>,
    selectedChapterId: String?,
    onSelectChapter: (String) -> Unit,
    autoSaveStatus: String,
    canUndo: Boolean,
    isRunningRewrite: Boolean,
    pendingSuggestion: PendingAiSuggestion?,
    onUpdateChapter: (previous: BookChapter, updated: BookChapter) -> Unit,
    onUndo: () -> Unit,
    onCreateChapter: (String) -> Unit,
    onDeleteChapter: (BookChapter) -> Unit,
    onMoveChapter: (from: Int, to: Int) -> Unit,
    onSplitChapter: (BookChapter, paragraphIndex: Int) -> Unit,
    onMergeWithNext: (BookChapter) -> Unit,
    onRequestAiRewrite: (BookChapter, selectedText: String, start: Int, end: Int, ChapterAiAction) -> Unit,
    onAcceptSuggestion: (BookChapter) -> Unit,
    onRejectSuggestion: () -> Unit,
    onSaveVersion: (label: String, note: String) -> Unit,
    onRestoreVersion: (BookVersion) -> Unit
) {
    val activeChapter = chapters.firstOrNull { it.id == selectedChapterId } ?: chapters.firstOrNull()

    var showNewChapterDialog by remember { mutableStateOf(false) }
    var chapterToDelete by remember { mutableStateOf<BookChapter?>(null) }
    var showSplitDialog by remember { mutableStateOf<BookChapter?>(null) }
    var showVersionsDialog by remember { mutableStateOf(false) }
    var showSaveVersionDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showCompactChapterList by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 820.dp

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                ) {
                    ChapterSidebar(
                        chapters = chapters,
                        activeChapterId = activeChapter?.id,
                        onSelectChapter = onSelectChapter,
                        onCreateClick = { showNewChapterDialog = true },
                        onMoveChapter = onMoveChapter,
                        onDeleteClick = { chapterToDelete = it }
                    )
                }
                VerticalDividerCompat()
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (activeChapter != null) {
                        ChapterEditorPane(
                            book = book,
                            chapter = activeChapter,
                            hasNextChapter = chapters.indexOfFirst { it.id == activeChapter.id } in 0 until chapters.lastIndex,
                            autoSaveStatus = autoSaveStatus,
                            canUndo = canUndo,
                            isRunningRewrite = isRunningRewrite,
                            onUpdateChapter = onUpdateChapter,
                            onUndo = onUndo,
                            onSplitClick = { showSplitDialog = activeChapter },
                            onMergeClick = { onMergeWithNext(activeChapter) },
                            onRequestAiRewrite = onRequestAiRewrite,
                            onSaveVersionClick = { showSaveVersionDialog = true },
                            onOpenVersionsClick = { showVersionsDialog = true },
                            onOpenPreviewClick = { showPreviewDialog = true },
                            onOpenChapterDrawerClick = null
                        )
                    } else {
                        EmptyManuscriptState(onCreateClick = { showNewChapterDialog = true })
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (activeChapter != null) {
                    ChapterEditorPane(
                        book = book,
                        chapter = activeChapter,
                        hasNextChapter = chapters.indexOfFirst { it.id == activeChapter.id } in 0 until chapters.lastIndex,
                        autoSaveStatus = autoSaveStatus,
                        canUndo = canUndo,
                        isRunningRewrite = isRunningRewrite,
                        onUpdateChapter = onUpdateChapter,
                        onUndo = onUndo,
                        onSplitClick = { showSplitDialog = activeChapter },
                        onMergeClick = { onMergeWithNext(activeChapter) },
                        onRequestAiRewrite = onRequestAiRewrite,
                        onSaveVersionClick = { showSaveVersionDialog = true },
                        onOpenVersionsClick = { showVersionsDialog = true },
                        onOpenPreviewClick = { showPreviewDialog = true },
                        onOpenChapterDrawerClick = { showCompactChapterList = true }
                    )
                } else {
                    EmptyManuscriptState(onCreateClick = { showNewChapterDialog = true })
                }
            }
        }
    }

    // Compact Chapter Selector Dialog
    if (showCompactChapterList) {
        Dialog(
            onDismissRequest = { showCompactChapterList = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.82f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("Capítulos (${chapters.size})", style = MaterialTheme.typography.titleLarge, color = Navy900)
                        IconButton(onClick = { showCompactChapterList = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar lista")
                        }
                    }
                    HorizontalDivider()
                    ChapterSidebar(
                        chapters = chapters,
                        activeChapterId = activeChapter?.id,
                        onSelectChapter = {
                            onSelectChapter(it)
                            showCompactChapterList = false
                        },
                        onCreateClick = {
                            showCompactChapterList = false
                            showNewChapterDialog = true
                        },
                        onMoveChapter = onMoveChapter,
                        onDeleteClick = { chapterToDelete = it }
                    )
                }
            }
        }
    }

    // Pending AI Suggestion Diff Review Dialog (Accept / Reject)
    if (pendingSuggestion != null && activeChapter != null && pendingSuggestion.chapterId == activeChapter.id) {
        AlertDialog(
            onDismissRequest = onRejectSuggestion,
            title = { Text("Revisar Sugestão: ${pendingSuggestion.action.labelPt}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Original:", style = MaterialTheme.typography.labelMedium, color = ErrorCrimson)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = pendingSuggestion.originalText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text("Sugestão da IA:", style = MaterialTheme.typography.labelMedium, color = SuccessGreen)
                    Surface(color = SuccessContainer.copy(alpha = 0.45f), shape = MaterialTheme.shapes.small) {
                        Text(
                            text = pendingSuggestion.suggestedText,
                            fontFamily = MerriweatherFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAcceptSuggestion(activeChapter) },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aceitar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onRejectSuggestion) {
                    Text("Rejeitar")
                }
            }
        )
    }

    // Create New Chapter
    if (showNewChapterDialog) {
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewChapterDialog = false },
            title = { Text("Novo Capítulo") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Título do capítulo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateChapter(newTitle)
                        showNewChapterDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy900)
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showNewChapterDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Confirm Chapter Deletion
    chapterToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            title = { Text("Excluir capítulo?") },
            text = { Text("Confirma a exclusão do Capítulo ${target.chapterNumber}: \"${target.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteChapter(target)
                        chapterToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorCrimson)
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { chapterToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    // Split Chapter Dialog
    showSplitDialog?.let { chap ->
        val paragraphs = remember(chap.content) {
            chap.content.split(Regex("\n\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        }
        AlertDialog(
            onDismissRequest = { showSplitDialog = null },
            title = { Text("Dividir Capítulo em Dois") },
            text = {
                if (paragraphs.size < 2) {
                    Text("Adicione ao menos 2 parágrafos separados por uma linha em branco para dividir este capítulo.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Text(
                                "Escolha a partir de qual parágrafo iniciar o novo capítulo:",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        itemsIndexed(paragraphs.drop(1)) { idx, para ->
                            val splitIdx = idx + 1
                            Surface(
                                color = Azure50,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSplitChapter(chap, splitIdx)
                                        showSplitDialog = null
                                    }
                            ) {
                                Text(
                                    text = "Iniciar Parte 2 em: \"${para.take(90)}...\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Navy900,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSplitDialog = null }) { Text("Fechar") }
            }
        )
    }

    // Save Version Dialog
    if (showSaveVersionDialog) {
        var label by remember { mutableStateOf("Revisão ${versions.size + 1}") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveVersionDialog = false },
            title = { Text("Salvar Versão do Manuscrito") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Nome da versão") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Observações (opcional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveVersion(label, note)
                        showSaveVersionDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy900)
                ) { Text("Salvar Versão") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveVersionDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Versions History Dialog
    if (showVersionsDialog) {
        val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showVersionsDialog = false },
            title = { Text("Versões Anteriores (${versions.size})") },
            text = {
                if (versions.isEmpty()) {
                    Text("Nenhuma versão salva ainda. Salve um marco de revisão para poder restaurá-lo depois.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(versions) { _, ver ->
                            val dateStr = ver.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Recente"
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(ver.label, style = MaterialTheme.typography.titleSmall, color = Navy900)
                                    Text(
                                        "$dateStr • ${ver.chapterCount} caps • ${ver.wordCount} palavras",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (ver.note.isNotBlank()) {
                                        Text(ver.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(onClick = {
                                        onRestoreVersion(ver)
                                        showVersionsDialog = false
                                    }) {
                                        Text("Restaurar esta versão")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionsDialog = false }) { Text("Fechar") }
            }
        )
    }

    // Full Book Reader Preview Dialog
    if (showPreviewDialog) {
        Dialog(
            onDismissRequest = { showPreviewDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(color = Navy900, contentColor = PureWhite) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Pré-visualização do Livro",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showPreviewDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar pré-visualização", tint = PureWhite)
                            }
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(book.title, style = MaterialTheme.typography.displaySmall, color = Navy900)
                            if (book.subtitle.isNotBlank()) {
                                Text(book.subtitle, style = MaterialTheme.typography.titleMedium, color = Azure600)
                            }
                            if (book.authorName.isNotBlank()) {
                                Text("Por ${book.authorName}", style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                        }
                        if (book.introduction.isNotBlank()) {
                            item {
                                Text("Introdução", style = MaterialTheme.typography.headlineMedium, color = Navy900)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(book.introduction, fontFamily = MerriweatherFamily, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        itemsIndexed(chapters) { _, chap ->
                            Column {
                                Text("CAPÍTULO ${chap.chapterNumber}", style = MaterialTheme.typography.labelSmall, color = Azure600)
                                Text(chap.title, style = MaterialTheme.typography.headlineMedium, color = Navy900)
                                if (chap.subtitle.isNotBlank()) {
                                    Text(chap.subtitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = chap.content,
                                    fontFamily = MerriweatherFamily,
                                    style = TextStyle(fontSize = 15.sp, lineHeight = 25.sp)
                                )
                                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalDividerCompat() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun ChapterSidebar(
    chapters: List<BookChapter>,
    activeChapterId: String?,
    onSelectChapter: (String) -> Unit,
    onCreateClick: () -> Unit,
    onMoveChapter: (from: Int, to: Int) -> Unit,
    onDeleteClick: (BookChapter) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("Capítulos (${chapters.size})", style = MaterialTheme.typography.titleSmall, color = Navy900)
            TextButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Novo")
            }
        }
        HorizontalDivider()
        LazyColumn(
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(chapters, key = { _, c -> c.id }) { idx, chap ->
                val isSelected = chap.id == activeChapterId
                Surface(
                    color = if (isSelected) Azure50 else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectChapter(chap.id) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CAP. ${chap.chapterNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Azure600 else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (chap.approved) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Aprovado", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            text = chap.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${chap.wordCount} palavras", style = MaterialTheme.typography.bodySmall)
                            Row {
                                IconButton(onClick = { onMoveChapter(idx, idx - 1) }, enabled = idx > 0, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Subir capítulo", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onMoveChapter(idx, idx + 1) }, enabled = idx < chapters.lastIndex, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Descer capítulo", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDeleteClick(chap) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir capítulo", tint = ErrorCrimson, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterEditorPane(
    book: BookProject,
    chapter: BookChapter,
    hasNextChapter: Boolean,
    autoSaveStatus: String,
    canUndo: Boolean,
    isRunningRewrite: Boolean,
    onUpdateChapter: (previous: BookChapter, updated: BookChapter) -> Unit,
    onUndo: () -> Unit,
    onSplitClick: () -> Unit,
    onMergeClick: () -> Unit,
    onRequestAiRewrite: (BookChapter, selectedText: String, start: Int, end: Int, ChapterAiAction) -> Unit,
    onSaveVersionClick: () -> Unit,
    onOpenVersionsClick: () -> Unit,
    onOpenPreviewClick: () -> Unit,
    onOpenChapterDrawerClick: (() -> Unit)?
) {
    var title by remember(chapter.id, chapter.title) { mutableStateOf(chapter.title) }
    var subtitle by remember(chapter.id, chapter.subtitle) { mutableStateOf(chapter.subtitle) }
    var partTitle by remember(chapter.id, chapter.partTitle) { mutableStateOf(chapter.partTitle) }
    var contentField by remember(chapter.id) {
        mutableStateOf(TextFieldValue(chapter.content, TextRange(chapter.content.length)))
    }

    LaunchedEffect(chapter.content) {
        if (chapter.content != contentField.text) {
            contentField = TextFieldValue(chapter.content, TextRange(chapter.content.length))
        }
    }

    val reviewMarkers = BookRepository.countReviewMarkers(contentField.text)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Utility Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            if (onOpenChapterDrawerClick != null) {
                OutlinedButton(onClick = onOpenChapterDrawerClick) {
                    Text("Cap. ${chapter.chapterNumber}: Trocar")
                }
            }
            Surface(color = Azure50, shape = MaterialTheme.shapes.small) {
                Text(
                    text = autoSaveStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = Azure600,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            OutlinedButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Desfazer")
            }
            OutlinedButton(onClick = onSaveVersionClick) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Salvar Versão")
            }
            OutlinedButton(onClick = onOpenVersionsClick) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Versões")
            }
            OutlinedButton(onClick = onOpenPreviewClick) {
                Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Visualizar")
            }
        }

        // Chapter Metadata & Approve Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = chapter.approved,
                onClick = {
                    onUpdateChapter(chapter, chapter.copy(approved = !chapter.approved))
                },
                label = { Text(if (chapter.approved) "Capítulo Aprovado" else "Aprovar Capítulo") },
                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            OutlinedButton(onClick = onSplitClick) {
                Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Dividir")
            }
            if (hasNextChapter) {
                OutlinedButton(onClick = onMergeClick) {
                    Icon(Icons.Default.CallMerge, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Unir")
                }
            }
        }

        if (reviewMarkers > 0) {
            Surface(color = WarningContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Atenção: $reviewMarkers marcador(es) [REVISAR] neste capítulo aguardam conferência.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        OutlinedTextField(
            value = partTitle,
            onValueChange = {
                partTitle = it
                onUpdateChapter(chapter, chapter.copy(partTitle = it, title = title, subtitle = subtitle, content = contentField.text))
            },
            label = { Text("Parte do livro (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                onUpdateChapter(chapter, chapter.copy(partTitle = partTitle, title = it, subtitle = subtitle, content = contentField.text))
            },
            label = { Text("Título do Capítulo ${chapter.chapterNumber}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = subtitle,
            onValueChange = {
                subtitle = it
                onUpdateChapter(chapter, chapter.copy(partTitle = partTitle, title = title, subtitle = it, content = contentField.text))
            },
            label = { Text("Subtítulo do capítulo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // AI Editorial Toolbar
        Card(
            colors = CardDefaults.cardColors(containerColor = Azure50),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val sel = contentField.selection
                val hasSelection = !sel.collapsed
                val selectedText = if (hasSelection) {
                    val start = minOf(sel.start, sel.end).coerceIn(0, contentField.text.length)
                    val end = maxOf(sel.start, sel.end).coerceIn(start, contentField.text.length)
                    contentField.text.substring(start, end)
                } else ""

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Azure600, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasSelection) {
                            "Ações IA no trecho selecionado (${selectedText.length} chars)"
                        } else {
                            "Ações IA no capítulo inteiro (ou selecione um trecho abaixo)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Navy900,
                        modifier = Modifier.weight(1f)
                    )
                    if (isRunningRewrite) {
                        CircularProgressIndicator(color = Azure600, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    ChapterAiAction.entries.forEach { action ->
                        AssistChip(
                            onClick = {
                                val start = minOf(sel.start, sel.end)
                                val end = maxOf(sel.start, sel.end)
                                onRequestAiRewrite(chapter, selectedText, start, end, action)
                            },
                            enabled = !isRunningRewrite,
                            label = { Text(action.labelPt) }
                        )
                    }
                }
            }
        }

        // Main Manuscript Text Area
        OutlinedTextField(
            value = contentField,
            onValueChange = { newVal ->
                val textChanged = newVal.text != contentField.text
                contentField = newVal
                if (textChanged) {
                    onUpdateChapter(
                        chapter,
                        chapter.copy(
                            partTitle = partTitle,
                            title = title,
                            subtitle = subtitle,
                            content = newVal.text
                        )
                    )
                }
            },
            label = { Text("Conteúdo do Capítulo (use '## Subtítulo' para subtítulos internos)") },
            textStyle = TextStyle(
                fontFamily = MerriweatherFamily,
                fontSize = 15.sp,
                lineHeight = 24.sp
            ),
            minLines = 14,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chapter_content_editor")
        )
    }
}

@Composable
private fun EmptyManuscriptState(onCreateClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Nenhum capítulo no manuscrito", style = MaterialTheme.typography.headlineSmall, color = Navy900)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Gere a estrutura automática na aba Estrutura ou crie capítulos manualmente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(onClick = onCreateClick, colors = ButtonDefaults.buttonColors(containerColor = Navy900)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Criar Primeiro Capítulo")
        }
    }
}

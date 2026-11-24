package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ai.ProposedBookStructure
import com.example.ai.ProposedChapterOutline
import com.example.model.AudioRecording
import com.example.model.BookChapter
import com.example.model.BookProject
import com.example.model.RecordingStatus
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

@Composable
fun StructureScreen(
    book: BookProject,
    recordings: List<AudioRecording>,
    chapters: List<BookChapter>,
    proposedStructure: ProposedBookStructure?,
    isAnalyzing: Boolean,
    generationProgress: String?,
    streamingPreview: String,
    onProposeStructure: () -> Unit,
    onUpdateBookFields: (Map<String, Any>) -> Unit,
    onUpdateProposedChapters: (List<ProposedChapterOutline>) -> Unit,
    onApproveAndGenerate: () -> Unit,
    onOpenEditor: () -> Unit
) {
    var selectedSectionTab by remember { mutableIntStateOf(0) } // 0 = Sumário/Capítulos, 1 = Pré-Textuais, 2 = Pós-Textuais
    val completedRecordings = recordings.count { it.parsedStatus == RecordingStatus.COMPLETED }

    var title by remember(book.id, book.title) { mutableStateOf(book.title) }
    var subtitle by remember(book.id, book.subtitle) { mutableStateOf(book.subtitle) }
    var authorName by remember(book.id, book.authorName) { mutableStateOf(book.authorName) }
    var presentation by remember(book.id, book.presentation) { mutableStateOf(book.presentation) }
    var dedication by remember(book.id, book.dedication) { mutableStateOf(book.dedication) }
    var acknowledgments by remember(book.id, book.acknowledgments) { mutableStateOf(book.acknowledgments) }
    var preface by remember(book.id, book.preface) { mutableStateOf(book.preface) }
    var introduction by remember(book.id, book.introduction) { mutableStateOf(book.introduction) }
    var conclusion by remember(book.id, book.conclusion) { mutableStateOf(book.conclusion) }
    var finalRemarks by remember(book.id, book.finalRemarks) { mutableStateOf(book.finalRemarks) }
    var bibliography by remember(book.id, book.bibliography) { mutableStateOf(book.bibliography) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Estrutura Editorial e Pré-Textuais",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Navy900
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "O Gemini analisa todas as $completedRecordings gravações transcritas em conjunto para propor capítulos lógicos, pré-textuais editáveis e pós-textuais sem inventar fatos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onProposeStructure,
                            enabled = completedRecordings > 0 && !isAnalyzing && generationProgress == null,
                            colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("analyze_structure_button")
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analisando...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Propor Estrutura IA")
                            }
                        }

                        if (chapters.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onOpenEditor,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Ir ao Editor (${chapters.size})")
                            }
                        }
                    }

                    if (book.aiGeneratedOutlineNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Azure50,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Azure600, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Nota Editorial: ${book.aiGeneratedOutlineNotes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Navy900
                                )
                            }
                        }
                    }
                }
            }
        }

        if (generationProgress != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Azure50),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Azure600, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(generationProgress, style = MaterialTheme.typography.titleSmall, color = Navy900)
                        }
                        if (streamingPreview.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = streamingPreview.takeLast(800),
                                fontFamily = MerriweatherFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item {
            TabRow(selectedTabIndex = selectedSectionTab) {
                Tab(
                    selected = selectedSectionTab == 0,
                    onClick = { selectedSectionTab = 0 },
                    text = { Text("Capítulos (${proposedStructure?.chapters?.size ?: chapters.size})") }
                )
                Tab(
                    selected = selectedSectionTab == 1,
                    onClick = { selectedSectionTab = 1 },
                    text = { Text("Pré-Textuais") }
                )
                Tab(
                    selected = selectedSectionTab == 2,
                    onClick = { selectedSectionTab = 2 },
                    text = { Text("Pós-Textuais") }
                )
            }
        }

        when (selectedSectionTab) {
            0 -> {
                val outlines = proposedStructure?.chapters ?: emptyList()
                if (outlines.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (chapters.isNotEmpty()) {
                                    "Você já possui ${chapters.size} capítulos gerados no Editor. Clique em \"Propor Estrutura IA\" apenas se desejar reorganizar um novo esboço a partir dos áudios."
                                } else {
                                    "Clique em \"Propor Estrutura IA\" acima para que o Gemini organize suas gravações em partes, capítulos e subtítulos."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(outlines) { idx, outline ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "CAPÍTULO ${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Azure600,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (outlines.size > 1) {
                                        IconButton(onClick = {
                                            val updated = outlines.toMutableList().apply { removeAt(idx) }
                                            onUpdateProposedChapters(updated)
                                        }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover capítulo do esboço", tint = ErrorCrimson)
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = outline.partTitle,
                                    onValueChange = { newVal ->
                                        val updated = outlines.toMutableList()
                                        updated[idx] = outline.copy(partTitle = newVal)
                                        onUpdateProposedChapters(updated)
                                    },
                                    label = { Text("Parte (opcional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = outline.title,
                                    onValueChange = { newVal ->
                                        val updated = outlines.toMutableList()
                                        updated[idx] = outline.copy(title = newVal)
                                        onUpdateProposedChapters(updated)
                                    },
                                    label = { Text("Título do Capítulo") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = outline.summary,
                                    onValueChange = { newVal ->
                                        val updated = outlines.toMutableList()
                                        updated[idx] = outline.copy(summary = newVal)
                                        onUpdateProposedChapters(updated)
                                    },
                                    label = { Text("Escopo / Assuntos abordados") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = {
                                val nextNum = outlines.size + 1
                                onUpdateProposedChapters(
                                    outlines + ProposedChapterOutline(
                                        partTitle = "",
                                        chapterNumber = nextNum,
                                        title = "Novo Capítulo $nextNum",
                                        subtitle = "",
                                        summary = "",
                                        sourceRecordingNumbers = listOf(1)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Adicionar Capítulo ao Esboço")
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            onUpdateBookFields(
                                mapOf(
                                    "title" to title.trim().ifBlank { book.title },
                                    "subtitle" to subtitle.trim(),
                                    "authorName" to authorName.trim(),
                                    "presentation" to presentation,
                                    "dedication" to dedication,
                                    "acknowledgments" to acknowledgments,
                                    "preface" to preface,
                                    "introduction" to introduction,
                                    "conclusion" to conclusion,
                                    "finalRemarks" to finalRemarks,
                                    "bibliography" to bibliography
                                )
                            )
                            onApproveAndGenerate()
                        },
                        enabled = completedRecordings > 0 && generationProgress == null,
                        colors = ButtonDefaults.buttonColors(containerColor = Azure600),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("approve_generate_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aprovar Estrutura e Gerar Manuscrito")
                    }
                }
            }

            1 -> {
                item {
                    Surface(
                        color = WarningContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Sugestões editáveis: Dedicatória, Agradecimentos e Prefácio são rascunhos assistidos. Revise e personalize com seus dados reais antes da publicação.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                item {
                    EditorialSectionFields(
                        fields = listOf(
                            Triple("Título do Livro", title) { v: String -> title = v },
                            Triple("Subtítulo", subtitle) { v: String -> subtitle = v },
                            Triple("Nome do Autor", authorName) { v: String -> authorName = v },
                            Triple("Apresentação da Obra", presentation) { v: String -> presentation = v },
                            Triple("Dedicatória (campo editável)", dedication) { v: String -> dedication = v },
                            Triple("Agradecimentos (campo editável)", acknowledgments) { v: String -> acknowledgments = v },
                            Triple("Prefácio (sugestão editável)", preface) { v: String -> preface = v },
                            Triple("Introdução", introduction) { v: String -> introduction = v }
                        ),
                        onSave = {
                            onUpdateBookFields(
                                mapOf(
                                    "title" to title.trim().ifBlank { book.title },
                                    "subtitle" to subtitle.trim(),
                                    "authorName" to authorName.trim(),
                                    "presentation" to presentation,
                                    "dedication" to dedication,
                                    "acknowledgments" to acknowledgments,
                                    "preface" to preface,
                                    "introduction" to introduction
                                )
                            )
                        }
                    )
                }
            }

            2 -> {
                item {
                    EditorialSectionFields(
                        fields = listOf(
                            Triple("Conclusão", conclusion) { v: String -> conclusion = v },
                            Triple("Considerações Finais", finalRemarks) { v: String -> finalRemarks = v },
                            Triple("Referências Bibliográficas", bibliography) { v: String -> bibliography = v }
                        ),
                        onSave = {
                            onUpdateBookFields(
                                mapOf(
                                    "conclusion" to conclusion,
                                    "finalRemarks" to finalRemarks,
                                    "bibliography" to bibliography
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorialSectionFields(
    fields: List<Triple<String, String, (String) -> Unit>>,
    onSave: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            fields.forEach { (label, value, setter) ->
                OutlinedTextField(
                    value = value,
                    onValueChange = setter,
                    label = { Text(label) },
                    minLines = if (label.contains("Título") || label.contains("Autor")) 1 else 3,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar Seções")
            }
        }
    }
}

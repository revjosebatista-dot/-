package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audio.InAppAudioRecorder
import com.example.model.AudioRecording
import com.example.model.BookProject
import com.example.model.RecordingStatus
import com.example.ui.theme.Azure50
import com.example.ui.theme.Azure600
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.MerriweatherFamily
import com.example.ui.theme.Navy900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AudioStudioScreen(
    book: BookProject,
    recordings: List<AudioRecording>,
    onImportAudioUris: (List<android.net.Uri>, translateToPtBr: Boolean) -> Unit,
    onAddRecordedAudio: (File, durationMs: Long, title: String, translateToPtBr: Boolean) -> Unit,
    onReprocessRecording: (AudioRecording) -> Unit,
    onMoveRecording: (index: Int, delta: Int) -> Unit,
    onToggleTranslation: (AudioRecording) -> Unit,
    onRenameRecording: (AudioRecording, String) -> Unit,
    onDeleteRecording: (AudioRecording) -> Unit,
    onGoToStructure: () -> Unit
) {
    val context = LocalContext.current
    var translateNewToPtBr by remember(book.id) { mutableStateOf(book.targetLanguage != "original") }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportAudioUris(uris, translateNewToPtBr)
        }
    }

    val recorder = remember(context) { InAppAudioRecorder(context) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }
    var micPermissionError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { recorder.stopSilently() }
    }

    LaunchedEffect(isRecordingAudio) {
        recordingSeconds = 0L
        while (isRecordingAudio) {
            delay(1000L)
            recordingSeconds++
        }
    }

    fun beginRecording() {
        runCatching {
            recorder.startRecording(book.id)
            isRecordingAudio = true
            micPermissionError = null
        }.onFailure { err ->
            micPermissionError = "Não foi possível iniciar o microfone: ${err.localizedMessage}"
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginRecording()
        } else {
            micPermissionError = "Permissão de microfone negada. Você ainda pode enviar arquivos MP3, WAV, M4A, AAC, OGG ou WEBM."
        }
    }

    var recordingToRename by remember { mutableStateOf<AudioRecording?>(null) }
    var recordingToDelete by remember { mutableStateOf<AudioRecording?>(null) }

    val completedCount = recordings.count { it.parsedStatus == RecordingStatus.COMPLETED }

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
                        text = "Estúdio de Áudio e Transcrição",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Navy900
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Envie um ou vários áudios (MP3, WAV, M4A, AAC, OGG, WEBM) ou grave sua voz. Arquivos longos são processados em partes com recuperação automática.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Translate, contentDescription = null, tint = Azure600)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Traduzir áudios estrangeiros para PT-BR", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Preserva nomes próprios, termos técnicos e citações",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = translateNewToPtBr,
                            onCheckedChange = { translateNewToPtBr = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("upload_audio_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enviar Áudios")
                        }

                        if (!isRecordingAudio) {
                            OutlinedButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) beginRecording()
                                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("record_voice_button")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gravar Voz")
                            }
                        } else {
                            Button(
                                onClick = {
                                    isRecordingAudio = false
                                    recorder.stopRecording()?.let { (file, dur) ->
                                        onAddRecordedAudio(
                                            file,
                                            dur,
                                            "Gravação ${recordings.size + 1}",
                                            translateNewToPtBr
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorCrimson),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("stop_record_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                val min = recordingSeconds / 60
                                val sec = recordingSeconds % 60
                                Text("Parar (%02d:%02d)".format(min, sec))
                            }
                        }
                    }

                    if (!micPermissionError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = WarningContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = micPermissionError!!,
                                color = WarningAmber,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        if (recordings.isEmpty()) {
            item {
                Surface(
                    color = Azure50,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = Azure600,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nenhuma gravação enviada ainda",
                            style = MaterialTheme.typography.titleMedium,
                            color = Navy900
                        )
                        Text(
                            text = "Toque em \"Enviar Áudios\" para selecionar gravações ou use \"Gravar Voz\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(recordings, key = { _, rec -> rec.id }) { index, rec ->
                RecordingCard(
                    index = index,
                    total = recordings.size,
                    recording = rec,
                    onMoveUp = { onMoveRecording(index, -1) },
                    onMoveDown = { onMoveRecording(index, +1) },
                    onReprocess = { onReprocessRecording(rec) },
                    onToggleTranslation = { onToggleTranslation(rec) },
                    onRename = { recordingToRename = rec },
                    onDelete = { recordingToDelete = rec }
                )
            }

            if (completedCount > 0) {
                item {
                    Button(
                        onClick = onGoToStructure,
                        colors = ButtonDefaults.buttonColors(containerColor = Azure600),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("go_to_structure_button")
                    ) {
                        Text("Analisar $completedCount gravação(ões) e Propor Estrutura")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }

    recordingToRename?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { recordingToRename = null },
            title = { Text("Renomear Gravação") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Título da gravação") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameRecording(target, newName)
                    recordingToRename = null
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { recordingToRename = null }) { Text("Cancelar") }
            }
        )
    }

    recordingToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = { Text("Remover gravação?") },
            text = { Text("Deseja excluir \"${target.title}\" e sua transcrição deste livro?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecording(target)
                        recordingToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorCrimson)
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordingCard(
    index: Int,
    total: Int,
    recording: AudioRecording,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onReprocess: () -> Unit,
    onToggleTranslation: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedTranscript by remember { mutableStateOf(false) }
    var selectedVersionTab by remember { mutableIntStateOf(1) } // 0 = Fiel, 1 = Editorial

    val statusColor = when (recording.parsedStatus) {
        RecordingStatus.COMPLETED -> SuccessContainer to SuccessGreen
        RecordingStatus.ERROR -> ErrorContainer to ErrorCrimson
        RecordingStatus.PROCESSING -> Azure50 to Azure600
        RecordingStatus.PENDING -> WarningContainer to WarningAmber
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = statusColor.first,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "#${index + 1} • ${recording.parsedStatus.labelPt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor.second,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (recording.detectedLanguage.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = recording.detectedLanguage,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Mover para cima")
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Mover para baixo")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = recording.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Navy900,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Renomear gravação")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir gravação", tint = ErrorCrimson)
                }
            }

            val sizeMb = recording.sizeBytes / (1024f * 1024f)
            val durSec = recording.durationMs / 1000L
            Text(
                text = "${recording.fileName} • ${"%.1f".format(sizeMb)} MB" +
                    if (durSec > 0) " • ${durSec / 60}m${"%02d".format(durSec % 60)}s" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (recording.parsedStatus == RecordingStatus.PROCESSING) {
                Spacer(modifier = Modifier.height(10.dp))
                val progress = if (recording.totalChunks > 0) {
                    recording.chunksCompleted.toFloat() / recording.totalChunks.toFloat()
                } else 0f
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0.05f, 1f) },
                    color = Azure600,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Processando parte ${recording.chunksCompleted + 1} de ${recording.totalChunks} com Gemini...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Azure600
                )
            }

            if (recording.errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = ErrorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorCrimson, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = recording.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorCrimson
                        )
                    }
                }
            }

            if (recording.contentSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = recording.contentSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (recording.keyTopics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recording.keyTopics.take(8).forEach { topic ->
                        AssistChip(onClick = {}, label = { Text(topic, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = recording.translateToPtBr,
                    onClick = onToggleTranslation,
                    label = { Text(if (recording.translateToPtBr) "Traduzir PT-BR" else "Original") }
                )
                OutlinedButton(onClick = onReprocess) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reprocessar")
                }
                if (recording.verbatimTranscript.isNotBlank() || recording.editorialTranscript.isNotBlank()) {
                    TextButton(onClick = { expandedTranscript = !expandedTranscript }) {
                        Text(if (expandedTranscript) "Ocultar Texto" else "Ver Transcrição")
                    }
                }
            }

            AnimatedVisibility(visible = expandedTranscript) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    TabRow(selectedTabIndex = selectedVersionTab) {
                        Tab(
                            selected = selectedVersionTab == 0,
                            onClick = { selectedVersionTab = 0 },
                            text = { Text("Transcrição Fiel") }
                        )
                        Tab(
                            selected = selectedVersionTab == 1,
                            onClick = { selectedVersionTab = 1 },
                            text = { Text("Texto Editorial") }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val displayed = if (selectedVersionTab == 0) {
                        recording.verbatimTranscript.ifBlank { "Sem transcrição fiel." }
                    } else {
                        recording.editorialTranscript.ifBlank { "Sem texto editorial." }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = displayed,
                            fontFamily = MerriweatherFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

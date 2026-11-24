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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.BookChapter
import com.example.model.BookProject
import com.example.model.QcIssueItem
import com.example.model.QcReport
import com.example.ui.theme.Azure50
import com.example.ui.theme.Azure600
import com.example.ui.theme.ErrorContainer
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.Navy900
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainer

@Composable
fun QualityControlScreen(
    book: BookProject,
    chapters: List<BookChapter>,
    latestReport: QcReport?,
    isRunningQc: Boolean,
    onRunQualityAudit: () -> Unit,
    onJumpToChapter: (String) -> Unit,
    onGoToExport: () -> Unit
) {
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
                        text = "Controle de Qualidade Editorial",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Navy900
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Verifica ortografia, gramática, repetições excessivas, parágrafos incompletos, capítulos duplicados, inconsistências de títulos, referências e trechos [REVISAR].",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRunQualityAudit,
                        enabled = !isRunningQc && chapters.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("run_qc_button")
                    ) {
                        if (isRunningQc) {
                            CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Auditando manuscrito...")
                        } else {
                            Icon(Icons.Default.FactCheck, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Executar Revisão Final")
                        }
                    }
                }
            }
        }

        if (latestReport == null) {
            item {
                Surface(color = Azure50, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nenhuma revisão executada ainda para \"${book.title}\". Execute a auditoria antes de exportar a versão final.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Navy900,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (latestReport.issueCount == 0L) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (latestReport.issueCount == 0L) SuccessGreen else WarningAmber
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Relatório (${latestReport.issueCount} ponto(s) para conferência)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Navy900
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(latestReport.summary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(color = WarningContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Conferência humana obrigatória: revise manualmente nomes próprios, citações bíblicas/técnicas, dedicatória, agradecimentos e eventuais marcadores [REVISAR] antes da publicação.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmber,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            val parsedIssues = latestReport.parsedIssues()
            items(parsedIssues) { issue ->
                QcIssueCard(issue = issue, onJumpToChapter = onJumpToChapter)
            }

            item {
                Button(
                    onClick = onGoToExport,
                    colors = ButtonDefaults.buttonColors(containerColor = Azure600),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Avançar para Diagramação e Exportação")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun QcIssueCard(
    issue: QcIssueItem,
    onJumpToChapter: (String) -> Unit
) {
    val (bg, fg, icon) = when (issue.severity.uppercase()) {
        "ERROR" -> Triple(ErrorContainer, ErrorCrimson, Icons.Default.ErrorOutline)
        "INFO" -> Triple(Azure50, Azure600, Icons.Default.Info)
        else -> Triple(WarningContainer, WarningAmber, Icons.Default.WarningAmber)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = bg, shape = MaterialTheme.shapes.small) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(issue.category, style = MaterialTheme.typography.labelSmall, color = fg)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(issue.location, style = MaterialTheme.typography.labelMedium, color = Navy900)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(issue.message, style = MaterialTheme.typography.bodyMedium)
            if (issue.chapterId.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { onJumpToChapter(issue.chapterId) }) {
                    Text("Abrir Capítulo no Editor")
                }
            }
        }
    }
}

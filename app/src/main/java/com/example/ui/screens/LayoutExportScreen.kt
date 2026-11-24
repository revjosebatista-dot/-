package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.export.ExportedFileResult
import com.example.model.BookChapter
import com.example.model.BookFontPreset
import com.example.model.BookProject
import com.example.model.ExportFormat
import com.example.model.PageSizePreset
import com.example.ui.theme.Azure600
import com.example.ui.theme.MerriweatherFamily
import com.example.ui.theme.Navy900
import com.example.ui.theme.PlayfairDisplayFamily
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate200
import com.example.ui.theme.SuccessContainer
import com.example.ui.theme.SuccessGreen

@Composable
fun LayoutExportScreen(
    book: BookProject,
    chapters: List<BookChapter>,
    isExporting: Boolean,
    lastExported: ExportedFileResult?,
    onUpdateLayout: (Map<String, Any>) -> Unit,
    onExport: (ExportFormat) -> Unit,
    onCopyToDestination: (android.net.Uri) -> Unit
) {
    val context = LocalContext.current

    var pageSize by remember(book.id, book.layoutPageSize) { mutableStateOf(PageSizePreset.fromCode(book.layoutPageSize)) }
    var customWidth by remember(book.id, book.layoutCustomWidthCm) { mutableStateOf(book.layoutCustomWidthCm.toString()) }
    var customHeight by remember(book.id, book.layoutCustomHeightCm) { mutableStateOf(book.layoutCustomHeightCm.toString()) }
    var marginInner by remember(book.id, book.layoutMarginInnerMm) { mutableLongStateOf(book.layoutMarginInnerMm) }
    var marginOuter by remember(book.id, book.layoutMarginOuterMm) { mutableLongStateOf(book.layoutMarginOuterMm) }
    var fontPreset by remember(book.id, book.layoutFontFamily) { mutableStateOf(BookFontPreset.fromCode(book.layoutFontFamily)) }
    var fontSizePt by remember(book.id, book.layoutFontSizePt) { mutableLongStateOf(book.layoutFontSizePt) }
    var lineSpacing by remember(book.id, book.layoutLineSpacing) { mutableDoubleStateOf(book.layoutLineSpacing) }
    var indentMm by remember(book.id, book.layoutParagraphIndentMm) { mutableLongStateOf(book.layoutParagraphIndentMm) }
    var justified by remember(book.id, book.layoutJustified) { mutableStateOf(book.layoutJustified) }
    var showHeaders by remember(book.id, book.layoutShowHeaders) { mutableStateOf(book.layoutShowHeaders) }
    var showPageNumbers by remember(book.id, book.layoutShowPageNumbers) { mutableStateOf(book.layoutShowPageNumbers) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            onCopyToDestination(uri)
        }
    }

    fun persistLayout() {
        val wCm = (customWidth.toDoubleOrNull() ?: 15.0).coerceIn(10.0, 30.0)
        val hCm = (customHeight.toDoubleOrNull() ?: 22.0).coerceIn(14.0, 40.0)
        onUpdateLayout(
            mapOf(
                "layoutPageSize" to pageSize.code,
                "layoutCustomWidthCm" to wCm,
                "layoutCustomHeightCm" to hCm,
                "layoutMarginInnerMm" to marginInner,
                "layoutMarginOuterMm" to marginOuter,
                "layoutFontFamily" to fontPreset.code,
                "layoutFontSizePt" to fontSizePt,
                "layoutLineSpacing" to lineSpacing,
                "layoutParagraphIndentMm" to indentMm,
                "layoutJustified" to justified,
                "layoutShowHeaders" to showHeaders,
                "layoutShowPageNumbers" to showPageNumbers
            )
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Export Actions Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Navy900),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Exportação Editorial",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Gera arquivos reais preservando sumário, margens espelhadas, quebras de capítulo, numeração e hierarquia tipográfica (${chapters.size} capítulos).",
                        style = MaterialTheme.typography.bodySmall,
                        color = PureWhite.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                persistLayout()
                                onExport(ExportFormat.PDF)
                            },
                            enabled = !isExporting,
                            colors = ButtonDefaults.buttonColors(containerColor = Azure600),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF")
                        }
                        Button(
                            onClick = {
                                persistLayout()
                                onExport(ExportFormat.DOCX)
                            },
                            enabled = !isExporting,
                            colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = Navy900),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_docx_button")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DOCX")
                        }
                        Button(
                            onClick = {
                                persistLayout()
                                onExport(ExportFormat.EPUB)
                            },
                            enabled = !isExporting,
                            colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = Navy900),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_epub_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EPUB")
                        }
                    }

                    if (isExporting) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Diagramando arquivo...", color = PureWhite, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Last Exported Result Actions
        if (lastExported != null) {
            item {
                Surface(
                    color = SuccessContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Arquivo pronto: ${lastExported.file.name}",
                            style = MaterialTheme.typography.titleSmall,
                            color = SuccessGreen
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = lastExported.format.mimeType
                                        putExtra(Intent.EXTRA_STREAM, lastExported.contentUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Compartilhar ou abrir livro"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Abrir / Compartilhar")
                            }
                            OutlinedButton(
                                onClick = { saveDocumentLauncher.launch(lastExported.file.name) }
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salvar")
                            }
                        }
                    }
                }
            }
        }

        // Live Book Page Preview
        item {
            val aspect = when (pageSize) {
                PageSizePreset.A5 -> 14.8f / 21.0f
                PageSizePreset.BOOK_16X23 -> 16.0f / 23.0f
                PageSizePreset.CUSTOM -> {
                    val w = (customWidth.toFloatOrNull() ?: 15f).coerceIn(10f, 30f)
                    val h = (customHeight.toFloatOrNull() ?: 22f).coerceIn(14f, 40f)
                    (w / h).coerceIn(0.5f, 1.0f)
                }
            }
            val previewFont = when (fontPreset) {
                BookFontPreset.MERRIWEATHER -> MerriweatherFamily
                BookFontPreset.PLAYFAIR_DISPLAY -> PlayfairDisplayFamily
                BookFontPreset.PLUS_JAKARTA_SANS -> PlusJakartaSansFamily
            }
            val sampleChap = chapters.firstOrNull()
            val sampleBody = sampleChap?.content?.take(340)
                ?: "Assim começa a diagramação do seu livro. As margens, a fonte e o espaçamento configurados abaixo refletem exatamente o arquivo final exportado."

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Prévia da Página (${pageSize.labelPt})", style = MaterialTheme.typography.titleSmall, color = Navy900)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = PureWhite,
                    border = BorderStroke(1.dp, Slate200),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = (marginInner.toFloat() * 0.9f).dp,
                                end = (marginOuter.toFloat() * 0.9f).dp,
                                top = 16.dp,
                                bottom = 14.dp
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (showHeaders) {
                                Text(
                                    text = book.title,
                                    fontFamily = previewFont,
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = "CAPÍTULO ${sampleChap?.chapterNumber ?: 1}",
                                fontSize = 8.sp,
                                color = Azure600,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = sampleChap?.title ?: "Título do Capítulo",
                                fontFamily = PlayfairDisplayFamily,
                                fontSize = (fontSizePt * 1.25f).sp,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val indentSpaces = " ".repeat((indentMm / 2).toInt().coerceIn(0, 8))
                            Text(
                                text = "$indentSpaces$sampleBody",
                                style = TextStyle(
                                    fontFamily = previewFont,
                                    fontSize = (fontSizePt * 0.85f).sp,
                                    lineHeight = (fontSizePt * 0.85f * lineSpacing.toFloat()).sp,
                                    textAlign = if (justified) TextAlign.Justify else TextAlign.Start
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            if (showPageNumbers) {
                                Text(
                                    text = "7",
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Formatting Controls Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Formato da Página", style = MaterialTheme.typography.titleMedium, color = Navy900)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageSizePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = pageSize == preset,
                                onClick = {
                                    pageSize = preset
                                    persistLayout()
                                },
                                label = { Text(preset.code) }
                            )
                        }
                    }

                    if (pageSize == PageSizePreset.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = customWidth,
                                onValueChange = { customWidth = it },
                                label = { Text("Largura (cm)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customHeight,
                                onValueChange = { customHeight = it },
                                label = { Text("Altura (cm)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Margem Interna: ${marginInner} mm | Externa: ${marginOuter} mm", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = marginInner.toFloat(),
                        onValueChange = { marginInner = it.toLong() },
                        onValueChangeFinished = { persistLayout() },
                        valueRange = 10f..35f
                    )
                    Slider(
                        value = marginOuter.toFloat(),
                        onValueChange = { marginOuter = it.toLong() },
                        onValueChangeFinished = { persistLayout() },
                        valueRange = 10f..35f
                    )

                    Text("Tipografia do Corpo", style = MaterialTheme.typography.titleMedium, color = Navy900)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BookFontPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = fontPreset == preset,
                                onClick = {
                                    fontPreset = preset
                                    persistLayout()
                                },
                                label = { Text(preset.labelPt) }
                            )
                        }
                    }

                    Text("Tamanho da Fonte: ${fontSizePt} pt", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = fontSizePt.toFloat(),
                        onValueChange = { fontSizePt = it.toLong() },
                        onValueChangeFinished = { persistLayout() },
                        valueRange = 9f..15f
                    )

                    Text("Entrelinha: ${"%.1f".format(lineSpacing)}x", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = lineSpacing.toFloat(),
                        onValueChange = { lineSpacing = ((it * 10).toInt() / 10.0) },
                        onValueChangeFinished = { persistLayout() },
                        valueRange = 1.1f..2.2f
                    )

                    Text("Recuo de Parágrafo: ${indentMm} mm", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = indentMm.toFloat(),
                        onValueChange = { indentMm = it.toLong() },
                        onValueChangeFinished = { persistLayout() },
                        valueRange = 0f..15f
                    )

                    ToggleRow("Alinhamento justificado", justified) {
                        justified = it
                        persistLayout()
                    }
                    ToggleRow("Cabeçalho corrente com título", showHeaders) {
                        showHeaders = it
                        persistLayout()
                    }
                    ToggleRow("Numeração de páginas", showPageNumbers) {
                        showPageNumbers = it
                        persistLayout()
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

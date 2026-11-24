package com.example.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.BookChapter
import com.example.model.BookFontPreset
import com.example.model.BookProject
import com.example.model.ExportFormat
import com.example.model.PageSizePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportedFileResult(
    val file: File,
    val contentUri: Uri,
    val format: ExportFormat
)

class BookExportService {

    companion object {
        private const val CM_TO_PT = 28.3464567f
        private const val MM_TO_PT = 2.83464567f
        private const val CM_TO_TWIPS = 566.929f
        private const val MM_TO_TWIPS = 56.6929f
    }

    suspend fun exportBook(
        context: Context,
        book: BookProject,
        chapters: List<BookChapter>,
        format: ExportFormat
    ): ExportedFileResult = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeTitle = book.title
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "livro_ia" }
            .take(40)
        val outFile = File(exportDir, "${safeTitle}_${System.currentTimeMillis()}.${format.extension}")

        when (format) {
            ExportFormat.PDF -> renderPdf(context, book, chapters, outFile)
            ExportFormat.DOCX -> renderDocx(book, chapters, outFile)
            ExportFormat.EPUB -> renderEpub(book, chapters, outFile)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        ExportedFileResult(file = outFile, contentUri = uri, format = format)
    }

    suspend fun copyFileToDestinationUri(
        context: Context,
        sourceFile: File,
        destinationUri: Uri
    ) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        } ?: error("Não foi possível gravar no destino selecionado.")
    }

    // =========================================================================
    // 1. PROFESSIONAL PDF BOOK RENDERER (Two-Pass TOC + Widow/Orphan Protection)
    // =========================================================================
    private fun renderPdf(
        context: Context,
        book: BookProject,
        chapters: List<BookChapter>,
        outFile: File
    ) {
        val preset = PageSizePreset.fromCode(book.layoutPageSize)
        val widthCm = if (preset == PageSizePreset.CUSTOM) book.layoutCustomWidthCm.toFloat() else preset.widthCm
        val heightCm = if (preset == PageSizePreset.CUSTOM) book.layoutCustomHeightCm.toFloat() else preset.heightCm

        val pageWidth = (widthCm * CM_TO_PT).toInt().coerceIn(280, 900)
        val pageHeight = (heightCm * CM_TO_PT).toInt().coerceIn(400, 1200)
        val innerMargin = (book.layoutMarginInnerMm.toFloat() * MM_TO_PT).coerceIn(18f, 140f)
        val outerMargin = (book.layoutMarginOuterMm.toFloat() * MM_TO_PT).coerceIn(18f, 140f)
        val topMargin = 52f
        val bottomMargin = 54f

        val bodyTypeface = loadTypeface(context, BookFontPreset.fromCode(book.layoutFontFamily))
        val displayTypeface = ResourcesCompat.getFont(context, R.font.playfair_display) ?: Typeface.SERIF
        val fontSize = book.layoutFontSizePt.toFloat().coerceIn(8f, 18f)
        val lineHeight = fontSize * book.layoutLineSpacing.toFloat().coerceIn(1.0f, 2.5f)
        val paragraphIndent = book.layoutParagraphIndentMm.toFloat() * MM_TO_PT

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bodyTypeface
            textSize = fontSize
            color = Color.rgb(18, 24, 38)
        }
        val heading1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(displayTypeface, Typeface.BOLD)
            textSize = fontSize * 1.7f
            color = Color.rgb(15, 39, 68)
        }
        val heading2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(displayTypeface, Typeface.BOLD)
            textSize = fontSize * 1.25f
            color = Color.rgb(23, 58, 99)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
            textSize = (fontSize * 0.85f).coerceAtLeast(8f)
            color = Color.rgb(37, 99, 235)
        }
        val chromePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bodyTypeface
            textSize = 8.5f
            color = Color.rgb(100, 116, 139)
        }

        // First pass: calculate TOC page numbers without emitting final file
        val tocEntries = mutableListOf<Pair<String, Int>>()
        renderPdfPass(
            book = book,
            chapters = chapters,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            innerMargin = innerMargin,
            outerMargin = outerMargin,
            topMargin = topMargin,
            bottomMargin = bottomMargin,
            lineHeight = lineHeight,
            paragraphIndent = paragraphIndent,
            bodyPaint = bodyPaint,
            heading1Paint = heading1Paint,
            heading2Paint = heading2Paint,
            labelPaint = labelPaint,
            chromePaint = chromePaint,
            tocEntriesInput = emptyList(),
            tocEntriesOutput = tocEntries
        ).close()

        // Second pass: emit real PDF with accurate Sumário page numbers
        val finalDoc = renderPdfPass(
            book = book,
            chapters = chapters,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            innerMargin = innerMargin,
            outerMargin = outerMargin,
            topMargin = topMargin,
            bottomMargin = bottomMargin,
            lineHeight = lineHeight,
            paragraphIndent = paragraphIndent,
            bodyPaint = bodyPaint,
            heading1Paint = heading1Paint,
            heading2Paint = heading2Paint,
            labelPaint = labelPaint,
            chromePaint = chromePaint,
            tocEntriesInput = tocEntries,
            tocEntriesOutput = null
        )

        FileOutputStream(outFile).use { fos ->
            finalDoc.writeTo(fos)
        }
        finalDoc.close()
    }

    private fun renderPdfPass(
        book: BookProject,
        chapters: List<BookChapter>,
        pageWidth: Int,
        pageHeight: Int,
        innerMargin: Float,
        outerMargin: Float,
        topMargin: Float,
        bottomMargin: Float,
        lineHeight: Float,
        paragraphIndent: Float,
        bodyPaint: Paint,
        heading1Paint: Paint,
        heading2Paint: Paint,
        labelPaint: Paint,
        chromePaint: Paint,
        tocEntriesInput: List<Pair<String, Int>>,
        tocEntriesOutput: MutableList<Pair<String, Int>>?
    ): PdfDocument {
        val doc = PdfDocument()
        var pageNumber = 0
        var currentPage: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var cursorY = topMargin

        fun leftMarginFor(p: Int): Float = if (p % 2 == 1) innerMargin else outerMargin
        fun rightMarginFor(p: Int): Float = if (p % 2 == 1) outerMargin else innerMargin
        fun contentWidthFor(p: Int): Float = (pageWidth - leftMarginFor(p) - rightMarginFor(p)).coerceAtLeast(120f)
        val maxY = pageHeight - bottomMargin

        fun finishCurrentPage() {
            val c = canvas
            val p = currentPage
            if (c != null && p != null) {
                if (pageNumber > 1) {
                    val lm = leftMarginFor(pageNumber)
                    val rm = pageWidth - rightMarginFor(pageNumber)
                    if (book.layoutShowHeaders) {
                        c.drawText(book.title.take(48), lm, 30f, chromePaint)
                    }
                    if (book.layoutShowPageNumbers) {
                        val numStr = pageNumber.toString()
                        val w = chromePaint.measureText(numStr)
                        val numX = if (pageNumber % 2 == 1) rm - w else lm
                        c.drawText(numStr, numX, pageHeight - 24f, chromePaint)
                    }
                }
                doc.finishPage(p)
            }
            currentPage = null
            canvas = null
        }

        fun startNewPage() {
            finishCurrentPage()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            currentPage = doc.startPage(info)
            canvas = currentPage!!.canvas
            cursorY = topMargin
        }

        fun ensureSpace(neededHeight: Float) {
            if (currentPage == null || cursorY + neededHeight > maxY) {
                startNewPage()
            }
        }

        fun drawParagraphs(text: String, applyIndent: Boolean) {
            val paragraphs = text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
            for (para in paragraphs) {
                if (para.startsWith("## ")) {
                    val sub = para.removePrefix("## ").trim()
                    val subLines = wrapLines(sub, heading2Paint, contentWidthFor(pageNumber))
                    val blockHeight = subLines.size * (heading2Paint.textSize * 1.35f) + lineHeight * 2f + 10f
                    // Never leave subtitle orphaned at bottom of page
                    if (cursorY + blockHeight > maxY) {
                        startNewPage()
                    } else {
                        cursorY += lineHeight * 0.7f
                    }
                    for (line in subLines) {
                        ensureSpace(heading2Paint.textSize * 1.4f)
                        canvas?.drawText(line, leftMarginFor(pageNumber), cursorY + heading2Paint.textSize, heading2Paint)
                        cursorY += heading2Paint.textSize * 1.35f
                    }
                    cursorY += lineHeight * 0.4f
                    continue
                }

                val contentW = contentWidthFor(pageNumber)
                val firstIndent = if (applyIndent) paragraphIndent else 0f
                val wrapped = wrapParagraphLines(para, bodyPaint, contentW, firstIndent)
                wrapped.forEachIndexed { idx, lineInfo ->
                    ensureSpace(lineHeight)
                    val lm = leftMarginFor(pageNumber)
                    val x = lm + (if (idx == 0) firstIndent else 0f)
                    val availableW = contentWidthFor(pageNumber) - (if (idx == 0) firstIndent else 0f)
                    val isLastLine = idx == wrapped.lastIndex
                    if (book.layoutJustified && !isLastLine && lineInfo.words.size > 1) {
                        drawJustifiedLine(canvas!!, lineInfo.words, x, cursorY + bodyPaint.textSize, availableW, bodyPaint)
                    } else {
                        canvas?.drawText(lineInfo.text, x, cursorY + bodyPaint.textSize, bodyPaint)
                    }
                    cursorY += lineHeight
                }
                cursorY += lineHeight * 0.35f
            }
        }

        // --- PAGE 1: COVER / TITLE PAGE ---
        startNewPage()
        val lm1 = leftMarginFor(1)
        val cw1 = contentWidthFor(1)
        cursorY = pageHeight * 0.28f
        val titleLines = wrapLines(book.title.ifBlank { "LIVRO IA" }, heading1Paint, cw1)
        for (line in titleLines) {
            canvas?.drawText(line, lm1, cursorY, heading1Paint)
            cursorY += heading1Paint.textSize * 1.3f
        }
        if (book.subtitle.isNotBlank()) {
            cursorY += 10f
            val subLines = wrapLines(book.subtitle, heading2Paint, cw1)
            for (line in subLines) {
                canvas?.drawText(line, lm1, cursorY, heading2Paint)
                cursorY += heading2Paint.textSize * 1.3f
            }
        }
        if (book.authorName.isNotBlank()) {
            cursorY = pageHeight * 0.78f
            canvas?.drawText(book.authorName, lm1, cursorY, bodyPaint)
        }
        canvas?.drawText("Edição preparada com LIVRO IA", lm1, pageHeight - 40f, chromePaint)

        // --- PRE-TEXTUAL SECTIONS ---
        val preSections = listOf(
            "Dedicatória" to book.dedication,
            "Agradecimentos" to book.acknowledgments,
            "Apresentação" to book.presentation,
            "Prefácio" to book.preface
        ).filter { it.second.isNotBlank() }

        for ((secTitle, secBody) in preSections) {
            startNewPage()
            tocEntriesOutput?.add(secTitle to pageNumber)
            canvas?.drawText(secTitle, leftMarginFor(pageNumber), cursorY + heading1Paint.textSize, heading1Paint)
            cursorY += heading1Paint.textSize * 1.8f
            drawParagraphs(secBody, applyIndent = true)
        }

        // --- SUMÁRIO (TABLE OF CONTENTS) ---
        startNewPage()
        canvas?.drawText("Sumário", leftMarginFor(pageNumber), cursorY + heading1Paint.textSize, heading1Paint)
        cursorY += heading1Paint.textSize * 1.9f
        val tocList = tocEntriesInput.ifEmpty {
            chapters.map { "Capítulo ${it.chapterNumber}: ${it.title}" to 0 }
        }
        for ((entryTitle, entryPage) in tocList) {
            ensureSpace(lineHeight * 1.2f)
            val lm = leftMarginFor(pageNumber)
            val rm = pageWidth - rightMarginFor(pageNumber)
            val pageText = if (entryPage > 0) entryPage.toString() else "—"
            val pW = bodyPaint.measureText(pageText)
            val maxTitleW = (rm - lm - pW - 16f).coerceAtLeast(80f)
            val clippedTitle = ellipsize(entryTitle, bodyPaint, maxTitleW)
            canvas?.drawText(clippedTitle, lm, cursorY + bodyPaint.textSize, bodyPaint)
            canvas?.drawText(pageText, rm - pW, cursorY + bodyPaint.textSize, bodyPaint)
            cursorY += lineHeight * 1.15f
        }

        // --- INTRODUÇÃO ---
        if (book.introduction.isNotBlank()) {
            startNewPage()
            tocEntriesOutput?.add("Introdução" to pageNumber)
            canvas?.drawText("Introdução", leftMarginFor(pageNumber), cursorY + heading1Paint.textSize, heading1Paint)
            cursorY += heading1Paint.textSize * 1.8f
            drawParagraphs(book.introduction, applyIndent = true)
        }

        // --- CHAPTERS (Each starts on a new page) ---
        var lastPart = ""
        for (chap in chapters) {
            startNewPage()
            if (chap.partTitle.isNotBlank() && chap.partTitle != lastPart) {
                lastPart = chap.partTitle
                canvas?.drawText(chap.partTitle.uppercase(Locale.getDefault()), leftMarginFor(pageNumber), cursorY + labelPaint.textSize, labelPaint)
                cursorY += labelPaint.textSize * 2.0f
            }
            val tocLabel = "Capítulo ${chap.chapterNumber}: ${chap.title}"
            tocEntriesOutput?.add(tocLabel to pageNumber)

            canvas?.drawText("CAPÍTULO ${chap.chapterNumber}", leftMarginFor(pageNumber), cursorY + labelPaint.textSize, labelPaint)
            cursorY += labelPaint.textSize * 1.6f

            val chapTitleLines = wrapLines(chap.title, heading1Paint, contentWidthFor(pageNumber))
            for (line in chapTitleLines) {
                ensureSpace(heading1Paint.textSize * 1.35f)
                canvas?.drawText(line, leftMarginFor(pageNumber), cursorY + heading1Paint.textSize, heading1Paint)
                cursorY += heading1Paint.textSize * 1.3f
            }
            if (chap.subtitle.isNotBlank()) {
                cursorY += 4f
                val subLines = wrapLines(chap.subtitle, heading2Paint, contentWidthFor(pageNumber))
                for (line in subLines) {
                    ensureSpace(heading2Paint.textSize * 1.3f)
                    canvas?.drawText(line, leftMarginFor(pageNumber), cursorY + heading2Paint.textSize, heading2Paint)
                    cursorY += heading2Paint.textSize * 1.3f
                }
            }
            cursorY += lineHeight * 0.9f
            drawParagraphs(chap.content, applyIndent = true)
        }

        // --- POST-TEXTUAL SECTIONS ---
        val postSections = listOf(
            "Conclusão" to book.conclusion,
            "Considerações Finais" to book.finalRemarks,
            "Referências Bibliográficas" to book.bibliography
        ).filter { it.second.isNotBlank() }

        for ((secTitle, secBody) in postSections) {
            startNewPage()
            tocEntriesOutput?.add(secTitle to pageNumber)
            canvas?.drawText(secTitle, leftMarginFor(pageNumber), cursorY + heading1Paint.textSize, heading1Paint)
            cursorY += heading1Paint.textSize * 1.8f
            drawParagraphs(secBody, applyIndent = true)
        }

        finishCurrentPage()
        return doc
    }

    private data class WrappedLine(val text: String, val words: List<String>)

    private fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        return wrapParagraphLines(text, paint, maxWidth, 0f).map { it.text }
    }

    private fun wrapParagraphLines(
        text: String,
        paint: Paint,
        maxWidth: Float,
        firstLineIndent: Float
    ): List<WrappedLine> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val result = mutableListOf<WrappedLine>()
        var currentWords = mutableListOf<String>()
        var isFirst = true

        for (word in words) {
            val limit = if (isFirst) (maxWidth - firstLineIndent).coerceAtLeast(40f) else maxWidth
            val candidate = if (currentWords.isEmpty()) word else (currentWords + word).joinToString(" ")
            if (paint.measureText(candidate) <= limit || currentWords.isEmpty()) {
                currentWords.add(word)
            } else {
                result.add(WrappedLine(currentWords.joinToString(" "), currentWords.toList()))
                currentWords = mutableListOf(word)
                isFirst = false
            }
        }
        if (currentWords.isNotEmpty()) {
            result.add(WrappedLine(currentWords.joinToString(" "), currentWords.toList()))
        }
        return result
    }

    private fun drawJustifiedLine(
        canvas: Canvas,
        words: List<String>,
        startX: Float,
        y: Float,
        targetWidth: Float,
        paint: Paint
    ) {
        if (words.size <= 1) {
            canvas.drawText(words.firstOrNull().orEmpty(), startX, y, paint)
            return
        }
        val wordsWidth = words.sumOf { paint.measureText(it).toDouble() }.toFloat()
        val gap = ((targetWidth - wordsWidth) / (words.size - 1)).coerceIn(paint.measureText(" ") * 0.6f, paint.measureText(" ") * 4.5f)
        var x = startX
        for (w in words) {
            canvas.drawText(w, x, y, paint)
            x += paint.measureText(w) + gap
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText("${text.substring(0, end)}...") > maxWidth) {
            end--
        }
        return "${text.substring(0, end)}..."
    }

    private fun loadTypeface(context: Context, preset: BookFontPreset): Typeface {
        val resId = when (preset) {
            BookFontPreset.MERRIWEATHER -> R.font.merriweather
            BookFontPreset.PLAYFAIR_DISPLAY -> R.font.playfair_display
            BookFontPreset.PLUS_JAKARTA_SANS -> R.font.plus_jakarta_sans
        }
        return ResourcesCompat.getFont(context, resId) ?: Typeface.SERIF
    }

    // =========================================================================
    // 2. FUNCTIONAL OOXML DOCX BOOK GENERATOR
    // =========================================================================
    private fun renderDocx(book: BookProject, chapters: List<BookChapter>, outFile: File) {
        val preset = PageSizePreset.fromCode(book.layoutPageSize)
        val widthCm = if (preset == PageSizePreset.CUSTOM) book.layoutCustomWidthCm.toFloat() else preset.widthCm
        val heightCm = if (preset == PageSizePreset.CUSTOM) book.layoutCustomHeightCm.toFloat() else preset.heightCm

        val pgW = (widthCm * CM_TO_TWIPS).toInt()
        val pgH = (heightCm * CM_TO_TWIPS).toInt()
        val marInner = (book.layoutMarginInnerMm.toFloat() * MM_TO_TWIPS).toInt()
        val marOuter = (book.layoutMarginOuterMm.toFloat() * MM_TO_TWIPS).toInt()
        val indentTwips = (book.layoutParagraphIndentMm.toFloat() * MM_TO_TWIPS).toInt()
        val fontSizeHalfPt = (book.layoutFontSizePt.toInt().coerceIn(8, 18) * 2)
        val lineSpacingTwips = (book.layoutLineSpacing * 240.0).toInt().coerceIn(240, 600)
        val jcVal = if (book.layoutJustified) "both" else "left"
        val fontName = when (BookFontPreset.fromCode(book.layoutFontFamily)) {
            BookFontPreset.MERRIWEATHER -> "Georgia"
            BookFontPreset.PLAYFAIR_DISPLAY -> "Palatino Linotype"
            BookFontPreset.PLUS_JAKARTA_SANS -> "Calibri"
        }

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>
  <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val docRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
</Relationships>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:pPr>
      <w:jc w:val="$jcVal"/>
      <w:spacing w:after="120" w:line="$lineSpacingTwips" w:lineRule="auto"/>
      <w:ind w:firstLine="$indentTwips"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="$fontName" w:hAnsi="$fontName"/>
      <w:sz w:val="$fontSizeHalfPt"/>
      <w:color w:val="121826"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr>
      <w:keepNext/>
      <w:pageBreakBefore/>
      <w:jc w:val="left"/>
      <w:ind w:firstLine="0"/>
      <w:spacing w:before="240" w:after="200"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Georgia" w:hAnsi="Georgia"/>
      <w:b/>
      <w:sz w:val="36"/>
      <w:color w:val="0F2744"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr>
      <w:keepNext/>
      <w:jc w:val="left"/>
      <w:ind w:firstLine="0"/>
      <w:spacing w:before="220" w:after="100"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Georgia" w:hAnsi="Georgia"/>
      <w:b/>
      <w:sz w:val="28"/>
      <w:color w:val="173A63"/>
    </w:rPr>
  </w:style>
</w:styles>"""

        val headerXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:p><w:pPr><w:jc w:val="left"/><w:ind w:firstLine="0"/></w:pPr><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="64748B"/></w:rPr><w:t>${if (book.layoutShowHeaders) xmlEsc(book.title) else ""}</w:t></w:r></w:p>
</w:hdr>"""

        val footerXml = if (book.layoutShowPageNumbers) {
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:p><w:pPr><w:jc w:val="right"/><w:ind w:firstLine="0"/></w:pPr><w:r><w:fldSimple w:instr=" PAGE "><w:r><w:rPr><w:sz w:val="18"/><w:color w:val="64748B"/></w:rPr><w:t>1</w:t></w:r></w:fldSimple></w:r></w:p>
</w:ftr>"""
        } else {
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p/></w:ftr>"""
        }

        val bodyBuilder = StringBuilder()
        fun addHeading1(text: String) {
            bodyBuilder.append("""<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>${xmlEsc(text)}</w:t></w:r></w:p>""")
        }
        fun addHeading2(text: String) {
            bodyBuilder.append("""<w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>${xmlEsc(text)}</w:t></w:r></w:p>""")
        }
        fun addNoIndentPara(text: String, bold: Boolean = false, sizeHalfPt: Int = fontSizeHalfPt) {
            val bTag = if (bold) "<w:b/>" else ""
            bodyBuilder.append("""<w:p><w:pPr><w:ind w:firstLine="0"/><w:jc w:val="left"/></w:pPr><w:r><w:rPr>$bTag<w:sz w:val="$sizeHalfPt"/></w:rPr><w:t xml:space="preserve">${xmlEsc(text)}</w:t></w:r></w:p>""")
        }
        fun addBodyText(raw: String) {
            raw.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { para ->
                if (para.startsWith("## ")) {
                    addHeading2(para.removePrefix("## ").trim())
                } else {
                    bodyBuilder.append("""<w:p><w:r><w:t xml:space="preserve">${xmlEsc(para)}</w:t></w:r></w:p>""")
                }
            }
        }

        // Cover
        addNoIndentPara(book.title.ifBlank { "LIVRO IA" }, bold = true, sizeHalfPt = 44)
        if (book.subtitle.isNotBlank()) addNoIndentPara(book.subtitle, bold = false, sizeHalfPt = 28)
        if (book.authorName.isNotBlank()) addNoIndentPara("Autor: ${book.authorName}", bold = false, sizeHalfPt = 24)

        // Pre-textual
        listOf(
            "Dedicatória" to book.dedication,
            "Agradecimentos" to book.acknowledgments,
            "Apresentação" to book.presentation,
            "Prefácio" to book.preface
        ).filter { it.second.isNotBlank() }.forEach { (t, b) ->
            addHeading1(t)
            addBodyText(b)
        }

        // Sumário
        addHeading1("Sumário")
        chapters.forEach { chap ->
            addNoIndentPara("Capítulo ${chap.chapterNumber} — ${chap.title}")
        }

        if (book.introduction.isNotBlank()) {
            addHeading1("Introdução")
            addBodyText(book.introduction)
        }

        // Chapters
        chapters.forEach { chap ->
            if (chap.partTitle.isNotBlank()) {
                addHeading1(chap.partTitle)
                addHeading2("Capítulo ${chap.chapterNumber}: ${chap.title}")
            } else {
                addHeading1("Capítulo ${chap.chapterNumber}: ${chap.title}")
            }
            if (chap.subtitle.isNotBlank()) {
                addNoIndentPara(chap.subtitle, bold = true, sizeHalfPt = fontSizeHalfPt + 4)
            }
            addBodyText(chap.content)
        }

        // Post-textual
        listOf(
            "Conclusão" to book.conclusion,
            "Considerações Finais" to book.finalRemarks,
            "Referências Bibliográficas" to book.bibliography
        ).filter { it.second.isNotBlank() }.forEach { (t, b) ->
            addHeading1(t)
            addBodyText(b)
        }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:body>
    $bodyBuilder
    <w:sectPr>
      <w:headerReference w:type="default" r:id="rId2"/>
      <w:footerReference w:type="default" r:id="rId3"/>
      <w:pgSz w:w="$pgW" w:h="$pgH"/>
      <w:pgMar w:top="1134" w:right="$marOuter" w:bottom="1134" w:left="$marInner" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypesXml)
            writeZipEntry(zip, "_rels/.rels", rootRelsXml)
            writeZipEntry(zip, "word/_rels/document.xml.rels", docRelsXml)
            writeZipEntry(zip, "word/styles.xml", stylesXml)
            writeZipEntry(zip, "word/header1.xml", headerXml)
            writeZipEntry(zip, "word/footer1.xml", footerXml)
            writeZipEntry(zip, "word/document.xml", documentXml)
        }
    }

    // =========================================================================
    // 3. FUNCTIONAL EPUB 3 E-BOOK GENERATOR
    // =========================================================================
    private fun renderEpub(book: BookProject, chapters: List<BookChapter>, outFile: File) {
        val bookUuid = "urn:uuid:${UUID.randomUUID()}"
        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val fontPreset = BookFontPreset.fromCode(book.layoutFontFamily)
        val textAlign = if (book.layoutJustified) "justify" else "left"
        val indentEm = (book.layoutParagraphIndentMm / 4.0).coerceIn(0.0, 4.0)

        val css = """
            body {
              font-family: ${fontPreset.cssFont};
              font-size: ${book.layoutFontSizePt}pt;
              line-height: ${book.layoutLineSpacing};
              margin: 6%;
              color: #0F172A;
            }
            h1 { color: #0F2744; font-size: 1.7em; margin-bottom: 0.4em; page-break-after: avoid; }
            h2 { color: #173A63; font-size: 1.25em; margin-top: 1.2em; page-break-after: avoid; }
            .part { color: #2563EB; text-transform: uppercase; letter-spacing: 0.1em; font-size: 0.85em; }
            p { text-align: $textAlign; text-indent: ${indentEm}em; margin: 0 0 0.6em 0; }
            .cover { text-align: center; margin-top: 25%; }
        """.trimIndent()

        data class EpubItem(val id: String, val href: String, val title: String, val xhtml: String)
        val items = mutableListOf<EpubItem>()

        fun wrapXhtml(title: String, bodyHtml: String): String = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="pt-BR" lang="pt-BR">
<head>
  <meta charset="UTF-8"/>
  <title>${xmlEsc(title)}</title>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
$bodyHtml
</body>
</html>"""

        fun textToHtml(raw: String): String = buildString {
            raw.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { para ->
                if (para.startsWith("## ")) {
                    appendLine("<h2>${xmlEsc(para.removePrefix("## ").trim())}</h2>")
                } else {
                    appendLine("<p>${xmlEsc(para)}</p>")
                }
            }
        }

        items.add(
            EpubItem(
                id = "cover",
                href = "cover.xhtml",
                title = "Capa",
                xhtml = wrapXhtml(
                    book.title,
                    """<div class="cover">
                       <h1>${xmlEsc(book.title.ifBlank { "LIVRO IA" })}</h1>
                       ${if (book.subtitle.isNotBlank()) "<h2>${xmlEsc(book.subtitle)}</h2>" else ""}
                       ${if (book.authorName.isNotBlank()) "<p style='text-align:center;text-indent:0;'>${xmlEsc(book.authorName)}</p>" else ""}
                     </div>"""
                )
            )
        )

        listOf(
            "dedication" to ("Dedicatória" to book.dedication),
            "ack" to ("Agradecimentos" to book.acknowledgments),
            "presentation" to ("Apresentação" to book.presentation),
            "preface" to ("Prefácio" to book.preface),
            "intro" to ("Introdução" to book.introduction)
        ).filter { it.second.second.isNotBlank() }.forEach { (id, pair) ->
            items.add(
                EpubItem(
                    id = id,
                    href = "$id.xhtml",
                    title = pair.first,
                    xhtml = wrapXhtml(pair.first, "<h1>${xmlEsc(pair.first)}</h1>\n${textToHtml(pair.second)}")
                )
            )
        }

        chapters.forEachIndexed { idx, chap ->
            val partHtml = if (chap.partTitle.isNotBlank()) "<div class='part'>${xmlEsc(chap.partTitle)}</div>" else ""
            val subHtml = if (chap.subtitle.isNotBlank()) "<h2>${xmlEsc(chap.subtitle)}</h2>" else ""
            items.add(
                EpubItem(
                    id = "chap_${idx + 1}",
                    href = "chap_${idx + 1}.xhtml",
                    title = "Capítulo ${chap.chapterNumber}: ${chap.title}",
                    xhtml = wrapXhtml(
                        chap.title,
                        "$partHtml\n<h1>Capítulo ${chap.chapterNumber}: ${xmlEsc(chap.title)}</h1>\n$subHtml\n${textToHtml(chap.content)}"
                    )
                )
            )
        }

        listOf(
            "conclusion" to ("Conclusão" to book.conclusion),
            "final" to ("Considerações Finais" to book.finalRemarks),
            "bib" to ("Referências Bibliográficas" to book.bibliography)
        ).filter { it.second.second.isNotBlank() }.forEach { (id, pair) ->
            items.add(
                EpubItem(
                    id = id,
                    href = "$id.xhtml",
                    title = pair.first,
                    xhtml = wrapXhtml(pair.first, "<h1>${xmlEsc(pair.first)}</h1>\n${textToHtml(pair.second)}")
                )
            )
        }

        val navItems = items.joinToString("\n") { "<li><a href=\"${it.href}\">${xmlEsc(it.title)}</a></li>" }
        val navXhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="pt-BR">
<head><meta charset="UTF-8"/><title>Sumário</title><link rel="stylesheet" type="text/css" href="styles.css"/></head>
<body><nav epub:type="toc" id="toc"><h1>Sumário</h1><ol>$navItems</ol></nav></body>
</html>"""

        val manifestItems = items.joinToString("\n") {
            """<item id="${it.id}" href="${it.href}" media-type="application/xhtml+xml"/>"""
        }
        val spineItems = items.joinToString("\n") { """<itemref idref="${it.id}"/>""" }

        val contentOpf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">$bookUuid</dc:identifier>
    <dc:title>${xmlEsc(book.title.ifBlank { "LIVRO IA" })}</dc:title>
    <dc:creator>${xmlEsc(book.authorName.ifBlank { "Autor" })}</dc:creator>
    <dc:language>${if (book.targetLanguage == "original") "und" else "pt-BR"}</dc:language>
    <meta property="dcterms:modified">$isoDate</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="css" href="styles.css" media-type="text/css"/>
    $manifestItems
  </manifest>
  <spine>
    $spineItems
  </spine>
</package>"""

        val containerXml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            // EPUB spec requires 'mimetype' as the first uncompressed STORED entry
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply { update(mimeBytes) }
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                this.crc = crc.value
            }
            zip.putNextEntry(mimeEntry)
            zip.write(mimeBytes)
            zip.closeEntry()

            writeZipEntry(zip, "META-INF/container.xml", containerXml)
            writeZipEntry(zip, "OEBPS/styles.css", css)
            writeZipEntry(zip, "OEBPS/nav.xhtml", navXhtml)
            writeZipEntry(zip, "OEBPS/content.opf", contentOpf)
            items.forEach { writeZipEntry(zip, "OEBPS/${it.href}", it.xhtml) }
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun xmlEsc(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

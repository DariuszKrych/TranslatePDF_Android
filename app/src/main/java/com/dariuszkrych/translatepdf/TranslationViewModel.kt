package com.dariuszkrych.translatepdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dariuszkrych.translatepdf.data.TranslationDatabase
import com.dariuszkrych.translatepdf.data.TranslationRecord
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

data class LanguageInfo(
    val code: String,
    val name: String,
    val downloaded: Boolean,
    val sizeMb: Float = 0f
)

// topY = top edge of the text area measured from the top of the page (both extraction methods)
data class TextBlock(
    val text: String,
    val x: Float,
    val topY: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val pageIndex: Int
)

class TranslationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TranslationDatabase.get(application)
    private val modelManager = RemoteModelManager.getInstance()

    var allLanguages by mutableStateOf<List<LanguageInfo>>(emptyList())
        private set
    var sourceLang by mutableStateOf("en")
    var targetLang by mutableStateOf("")

    var selectedPdfUri by mutableStateOf<Uri?>(null)
        private set
    var selectedFileName by mutableStateOf("")
        private set
    var extractionMethod by mutableStateOf("direct")
    var isTranslating by mutableStateOf(false)
        private set
    var translationProgress by mutableStateOf("")
        private set
    var translationPercent by mutableFloatStateOf(0f)
        private set
    var translatedPdfFile by mutableStateOf<File?>(null)
        private set

    var pdfPages by mutableStateOf<List<Bitmap>>(emptyList())
        private set

    var historyRecords by mutableStateOf<List<TranslationRecord>>(emptyList())
        private set
    var historySearch by mutableStateOf("")

    var downloadingLangs by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        PDFBoxResourceLoader.init(application)
        refreshLanguages()
    }

    // ── Language management ──────────────────────────────────────────────

    fun refreshLanguages() {
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { downloadedModels ->
                val downloadedCodes = downloadedModels.map { it.language }.toSet()
                val modelSizes = getDownloadedModelSizes()
                allLanguages = TranslateLanguage.getAllLanguages().map { code ->
                    LanguageInfo(
                        code = code,
                        name = languageDisplayName(code),
                        downloaded = code in downloadedCodes,
                        sizeMb = modelSizes[code] ?: 0f
                    )
                }.sortedBy { it.name }
            }
    }

    private fun getDownloadedModelSizes(): Map<String, Float> {
        val sizes = mutableMapOf<String, Float>()
        val context = getApplication<Application>()

        // ML Kit models are managed by Google Play Services and stored in various locations.
        // Search the entire app data directory for translate model folders.
        val appDataDir = context.filesDir.parentFile ?: return sizes
        val candidates = mutableListOf<File>()

        // Walk all subdirs looking for directories whose path contains "translate" and "model"
        appDataDir.walkTopDown()
            .maxDepth(6)
            .filter { it.isDirectory }
            .forEach { dir ->
                val pathLower = dir.absolutePath.lowercase()
                if (pathLower.contains("translate") && pathLower.contains("model")) {
                    candidates.add(dir)
                }
            }

        for (modelsDir in candidates) {
            modelsDir.listFiles()?.forEach { langDir ->
                if (langDir.isDirectory) {
                    val totalBytes = langDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    val mb = totalBytes / (1024f * 1024f)
                    if (mb > 0.01f) {
                        // The directory name is typically the language code
                        val code = langDir.name.lowercase()
                        // Only store if this looks like a lang code we know about
                        if (TranslateLanguage.getAllLanguages().contains(code)) {
                            val existing = sizes[code] ?: 0f
                            if (mb > existing) sizes[code] = mb
                        }
                    }
                }
            }
        }
        return sizes
    }

    fun downloadLanguage(code: String) {
        downloadingLangs = downloadingLangs + code
        val model = TranslateRemoteModel.Builder(code).build()
        val conditions = DownloadConditions.Builder().build()
        modelManager.download(model, conditions)
            .addOnSuccessListener {
                downloadingLangs = downloadingLangs - code
                refreshLanguages()
            }
            .addOnFailureListener {
                downloadingLangs = downloadingLangs - code
            }
    }

    fun deleteLanguage(code: String) {
        val model = TranslateRemoteModel.Builder(code).build()
        modelManager.deleteDownloadedModel(model)
            .addOnSuccessListener { refreshLanguages() }
    }

    // ── PDF selection ────────────────────────────────────────────────────

    fun setPdfFile(uri: Uri, fileName: String) {
        selectedPdfUri = uri
        selectedFileName = fileName
        translatedPdfFile = null
        pdfPages = emptyList()
    }

    // ── Translation pipeline ─────────────────────────────────────────────

    fun translate() {
        val uri = selectedPdfUri ?: return
        if (sourceLang.isEmpty() || targetLang.isEmpty()) return
        if (sourceLang == targetLang) return

        isTranslating = true
        translationProgress = "Extracting text..."
        translationPercent = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                translationPercent = 5f
                val blocks = if (extractionMethod == "direct") {
                    extractTextDirect(uri)
                } else {
                    extractTextOcr(uri)
                }
                translationPercent = 10f

                if (blocks.isEmpty()) {
                    translationProgress = "No text found in PDF."
                    translationPercent = 0f
                    isTranslating = false
                    return@launch
                }

                val mergedBlocks = mergeNearbyBlocks(blocks)

                translationProgress = "Translating..."
                val translatedBlocks = translateBlocks(mergedBlocks)

                translationProgress = "Generating PDF..."
                translationPercent = 80f
                val outputFile = generateTranslatedPdf(uri, translatedBlocks)
                translationPercent = 95f

                translatedPdfFile = outputFile
                renderPdf(outputFile)

                db.translationDao().insert(
                    TranslationRecord(
                        fileName = selectedFileName,
                        sourceLang = sourceLang,
                        targetLang = targetLang,
                        timestamp = System.currentTimeMillis(),
                        outputPath = outputFile.absolutePath
                    )
                )

                translationPercent = 100f
                translationProgress = "Done!"
                isTranslating = false
            } catch (e: Exception) {
                translationProgress = "Error: ${e.message}"
                translationPercent = 0f
                isTranslating = false
            }
        }
    }

    // ── Direct text extraction (PdfBox) ──────────────────────────────────

    private fun extractTextDirect(uri: Uri): List<TextBlock> {
        val context = getApplication<Application>()
        val blocks = mutableListOf<TextBlock>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            val doc = PDDocument.load(input)
            doc.use {
                for (pageIndex in 0 until doc.numberOfPages) {
                    val stripper = object : PDFTextStripper() {
                        init {
                            startPage = pageIndex + 1
                            endPage = pageIndex + 1
                        }

                        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                            if (text.isBlank() || textPositions.isEmpty()) return
                            val firstPos = textPositions.first()
                            val lastPos = textPositions.last()

                            val x = firstPos.xDirAdj
                            val baseline = firstPos.yDirAdj
                            val height = firstPos.heightDir
                            val width = (lastPos.xDirAdj + lastPos.widthDirAdj) - x

                            // baseline is measured from page top.
                            // topY = baseline - ascent ≈ baseline - height
                            val topY = baseline - height

                            blocks.add(
                                TextBlock(
                                    text = text.trim(),
                                    x = x,
                                    topY = topY,
                                    width = width,
                                    height = height,
                                    fontSize = firstPos.fontSize,
                                    pageIndex = pageIndex
                                )
                            )
                            super.writeString(text, textPositions)
                        }
                    }
                    stripper.getText(doc)
                }
            }
        }
        return blocks
    }

    // ── OCR text extraction (ML Kit) ─────────────────────────────────────

    private fun getOcrRecognizer(): TextRecognizer {
        return when (sourceLang) {
            "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            "hi", "mr", "ne", "sa" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    private suspend fun extractTextOcr(uri: Uri): List<TextBlock> {
        val context = getApplication<Application>()
        val blocks = mutableListOf<TextBlock>()
        val recognizer = getOcrRecognizer()

        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return blocks
        val renderer = PdfRenderer(fd)

        for (pageIndex in 0 until renderer.pageCount) {
            val page = renderer.openPage(pageIndex)
            val scale = 3 // Higher scale for better OCR accuracy
            val bitmapW = page.width * scale
            val bitmapH = page.height * scale
            val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            // PdfRenderer page dimensions are in points (1/72 inch), same as PDF mediaBox
            val pdfPageWidth = page.width.toFloat()
            val pdfPageHeight = page.height.toFloat()
            page.close()

            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()

            val scaleX = pdfPageWidth / bitmapW.toFloat()
            val scaleY = pdfPageHeight / bitmapH.toFloat()

            for (block in result.textBlocks) {
                val box = block.boundingBox ?: continue
                blocks.add(
                    TextBlock(
                        text = block.text,
                        x = box.left * scaleX,
                        topY = box.top * scaleY,
                        width = box.width() * scaleX,
                        height = box.height() * scaleY,
                        fontSize = (box.height() * scaleY * 0.6f).coerceIn(6f, 48f),
                        pageIndex = pageIndex
                    )
                )
            }
            bitmap.recycle()
        }
        renderer.close()
        fd.close()
        recognizer.close()
        return blocks
    }

    // ── Merge nearby blocks ────────────────────────────────────────────────

    private fun mergeNearbyBlocks(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.isEmpty()) return blocks
        val byPage = blocks.groupBy { it.pageIndex }
        val result = mutableListOf<TextBlock>()

        for ((_, pageBlocks) in byPage) {
            // Sort by topY then x
            val sorted = pageBlocks.sortedWith(compareBy({ it.topY }, { it.x }))
            val merged = mutableListOf<TextBlock>()
            val used = BooleanArray(sorted.size)

            for (i in sorted.indices) {
                if (used[i]) continue
                var current = sorted[i]
                used[i] = true

                // Keep merging until no more neighbors found
                var changed = true
                while (changed) {
                    changed = false
                    for (j in sorted.indices) {
                        if (used[j]) continue
                        val other = sorted[j]
                        if (shouldMerge(current, other)) {
                            current = mergeTwo(current, other)
                            used[j] = true
                            changed = true
                        }
                    }
                }
                merged.add(current)
            }
            result.addAll(merged)
        }
        return result
    }

    private fun shouldMerge(a: TextBlock, b: TextBlock): Boolean {
        // Check if blocks are on the same page (caller already groups by page)
        // Check vertical proximity: gap between bottom of one and top of other
        val aBottom = a.topY + a.height
        val bBottom = b.topY + b.height
        val verticalGap = if (a.topY < b.topY) {
            b.topY - aBottom
        } else {
            a.topY - bBottom
        }
        val lineHeight = minOf(a.height, b.height)
        // Merge if vertical gap is less than half a line height
        if (verticalGap > lineHeight * 0.5f) return false

        // Check horizontal overlap: blocks should overlap or be close horizontally
        val aRight = a.x + a.width
        val bRight = b.x + b.width
        val horizontalOverlap = minOf(aRight, bRight) - maxOf(a.x, b.x)
        val minWidth = minOf(a.width, b.width)
        // Overlap at least 30% of the smaller block's width, or gap is very small
        return horizontalOverlap > -lineHeight && horizontalOverlap > minWidth * -0.3f
    }

    private fun mergeTwo(a: TextBlock, b: TextBlock): TextBlock {
        val minX = minOf(a.x, b.x)
        val minTopY = minOf(a.topY, b.topY)
        val maxBottom = maxOf(a.topY + a.height, b.topY + b.height)
        val maxRight = maxOf(a.x + a.width, b.x + b.width)
        // Order text by vertical position
        val (top, bottom) = if (a.topY <= b.topY) a to b else b to a
        return TextBlock(
            text = top.text + "\n" + bottom.text,
            x = minX,
            topY = minTopY,
            width = maxRight - minX,
            height = maxBottom - minTopY,
            fontSize = maxOf(a.fontSize, b.fontSize),
            pageIndex = a.pageIndex
        )
    }

    // ── Translation ──────────────────────────────────────────────────────

    private suspend fun translateBlocks(blocks: List<TextBlock>): List<Pair<TextBlock, String>> {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions).await()

        val results = mutableListOf<Pair<TextBlock, String>>()
        for ((i, block) in blocks.withIndex()) {
            val translated = translator.translate(block.text).await()
            results.add(block to translated)
            translationPercent = 10f + (70f * (i + 1) / blocks.size)
        }
        translator.close()
        return results
    }

    // ── PDF generation ───────────────────────────────────────────────────

    private fun generateTranslatedPdf(
        originalUri: Uri,
        translatedBlocks: List<Pair<TextBlock, String>>
    ): File {
        val context = getApplication<Application>()
        val inputStream = context.contentResolver.openInputStream(originalUri)!!
        val doc = PDDocument.load(inputStream)
        val font = loadUnicodeFont(doc)

        val blocksByPage = translatedBlocks.groupBy { it.first.pageIndex }

        for ((pageIndex, pageBlocks) in blocksByPage) {
            if (pageIndex >= doc.numberOfPages) continue
            val page = doc.getPage(pageIndex)
            val pageHeight = page.mediaBox.height
            val pageWidth = page.mediaBox.width

            val contentStream = PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true
            )

            for ((original, translated) in pageBlocks) {
                // Convert from "topY from page top" to PDF coords (origin at bottom-left)
                val boxTopPdf = pageHeight - original.topY
                val boxBottomPdf = boxTopPdf - original.height

                // White rectangle covering the ORIGINAL text area fully
                val coverX = (original.x - 2f).coerceAtLeast(0f)
                val coverBottom = boxBottomPdf - 2f
                val coverW = (original.width + 4f).coerceAtMost(pageWidth - coverX)
                val coverH = original.height + 4f

                contentStream.setNonStrokingColor(1f, 1f, 1f)
                contentStream.addRect(coverX, coverBottom, coverW, coverH)
                contentStream.fill()

                val safeText = buildSafeString(font, translated)
                if (safeText.isBlank()) continue

                val availableWidth = original.width
                val availableHeight = original.height
                val lineSpacing = 1.2f

                // Word-wrap text into lines, breaking long words if needed
                fun wrapText(text: String, fs: Float): List<String> {
                    val words = text.replace("\n", " ").split(" ").filter { it.isNotEmpty() }
                    if (words.isEmpty()) return emptyList()
                    val lines = mutableListOf<String>()
                    var currentLine = ""

                    for (word in words) {
                        // If a single word is wider than the box, break it by character
                        if (getStringWidth(font, word, fs) > availableWidth && word.length > 1) {
                            if (currentLine.isNotEmpty()) {
                                lines.add(currentLine)
                                currentLine = ""
                            }
                            var chunk = ""
                            for (ch in word) {
                                val test = chunk + ch
                                if (getStringWidth(font, test, fs) > availableWidth && chunk.isNotEmpty()) {
                                    lines.add(chunk)
                                    chunk = ch.toString()
                                } else {
                                    chunk = test
                                }
                            }
                            currentLine = chunk
                            continue
                        }

                        val test = if (currentLine.isEmpty()) word else "$currentLine $word"
                        if (getStringWidth(font, test, fs) <= availableWidth) {
                            currentLine = test
                        } else {
                            if (currentLine.isNotEmpty()) lines.add(currentLine)
                            currentLine = word
                        }
                    }
                    if (currentLine.isNotEmpty()) lines.add(currentLine)
                    return lines
                }

                // Check if text fits in the box at a given font size
                fun fitsInBox(fs: Float): Boolean {
                    val wrapped = wrapText(safeText, fs)
                    if (wrapped.isEmpty()) return true
                    val totalH = wrapped.size * fs * lineSpacing
                    if (totalH > availableHeight + 0.5f) return false
                    // Every line must fit width (wrapText handles this, but verify)
                    return wrapped.all { getStringWidth(font, it, fs) <= availableWidth + 0.5f }
                }

                // Binary search for the largest font size that fits the box
                val maxFontSize = original.fontSize.coerceIn(4f, 72f)
                var lo = 4f
                var hi = maxFontSize

                // First check: does the original font size fit?
                if (!fitsInBox(hi)) {
                    // Binary search downward to find the largest size that fits
                    for (step in 0 until 20) {
                        val mid = (lo + hi) / 2f
                        if (fitsInBox(mid)) lo = mid else hi = mid
                    }
                    hi = lo // use the largest fitting size
                }

                // Now try scaling UP if there is spare space (text could be larger)
                // Only scale up to the original font size, not beyond
                if (hi < maxFontSize) {
                    lo = hi
                    var tryHi = maxFontSize
                    for (step in 0 until 15) {
                        val mid = (lo + tryHi) / 2f
                        if (fitsInBox(mid)) lo = mid else tryHi = mid
                    }
                    hi = lo
                }

                val fontSize = hi.coerceAtLeast(4f)
                val lines = wrapText(safeText, fontSize)
                if (lines.isEmpty()) continue

                // Center text vertically within the box
                val totalTextHeight = lines.size * fontSize * lineSpacing
                val verticalPadding = (availableHeight - totalTextHeight) / 2f
                val ascent = fontSize * 0.8f
                val firstBaseline = boxTopPdf - verticalPadding - ascent
                val lineStep = fontSize * lineSpacing

                contentStream.setNonStrokingColor(0f, 0f, 0f)
                for ((lineIndex, line) in lines.withIndex()) {
                    val baseline = firstBaseline - (lineIndex * lineStep)
                    // Hard stop: never draw outside the box
                    if (baseline < boxBottomPdf) break
                    if (baseline > boxTopPdf) continue
                    contentStream.beginText()
                    contentStream.setFont(font, fontSize)
                    contentStream.newLineAtOffset(original.x, baseline)
                    contentStream.showText(line)
                    contentStream.endText()
                }
            }
            contentStream.close()
        }

        val outputFile = File(context.filesDir, "translated_${System.currentTimeMillis()}.pdf")
        doc.save(outputFile)
        doc.close()
        inputStream.close()
        return outputFile
    }

    private fun loadUnicodeFont(doc: PDDocument): PDFont {
        val fontPaths = listOf(
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/NotoSansCJK-Regular.ttc",
            "/system/fonts/DroidSans.ttf",
            "/system/fonts/Roboto-Regular.ttf"
        )
        for (path in fontPaths) {
            val fontFile = File(path)
            if (fontFile.exists()) {
                return try {
                    PDType0Font.load(doc, fontFile)
                } catch (_: Exception) {
                    continue
                }
            }
        }
        return PDType1Font.HELVETICA
    }

    private fun getStringWidth(font: PDFont, text: String, fontSize: Float): Float {
        return try {
            font.getStringWidth(buildSafeString(font, text)) / 1000f * fontSize
        } catch (_: Exception) {
            text.length * fontSize * 0.5f
        }
    }

    private fun buildSafeString(font: PDFont, text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            try {
                font.encode(ch.toString())
                sb.append(ch)
            } catch (_: Exception) {
                sb.append(' ')
            }
        }
        return sb.toString()
    }

    // ── PDF viewer ───────────────────────────────────────────────────────

    fun renderPdf(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val bitmaps = mutableListOf<Bitmap>()
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            renderer.close()
            fd.close()
            withContext(Dispatchers.Main) { pdfPages = bitmaps }
        }
    }

    // ── History ──────────────────────────────────────────────────────────

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val records = if (historySearch.isBlank()) {
                db.translationDao().getAll()
            } else {
                db.translationDao().search(historySearch)
            }
            withContext(Dispatchers.Main) { historyRecords = records }
        }
    }

    fun deleteHistoryRecord(record: TranslationRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            db.translationDao().delete(record)
            File(record.outputPath).delete()
            loadHistory()
        }
    }

    fun openTranslatedPdf(record: TranslationRecord) {
        val file = File(record.outputPath)
        if (file.exists()) {
            translatedPdfFile = file
            renderPdf(file)
        }
    }

    companion object {
        fun languageDisplayName(code: String): String {
            return java.util.Locale.forLanguageTag(code).displayLanguage
        }
    }
}

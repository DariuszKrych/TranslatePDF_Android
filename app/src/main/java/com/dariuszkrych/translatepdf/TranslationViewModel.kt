package com.dariuszkrych.translatepdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.createBitmap
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
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * One entry in the "Languages" screen list — a BCP-47 code, its human-readable
 * name, whether the offline ML Kit translation model is downloaded, and the
 * approximate on-disk size in MB.
 */
data class LanguageInfo(
    val code: String,
    val name: String,
    val downloaded: Boolean,
    val sizeMb: Float = 0f
)

/**
 * A rectangular chunk of text extracted from a PDF page, used for both extraction
 * strategies (direct text stripping and OCR).
 *
 * `topY` is measured from the TOP of the page so both strategies agree; the PDF
 * generator later converts this back to PDF coordinates (origin at bottom-left).
 */
data class TextBlock(
    val text: String,
    val x: Float,
    val topY: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val pageIndex: Int
)

/**
 * The single shared ViewModel backing Home / Languages / History / PdfViewer.
 *
 * Extends `AndroidViewModel` because we need an `Application` context for
 * ContentResolver, filesDir, SharedPreferences and ML Kit init.
 *
 * All long-running work (OCR, translation, PDF generation) runs inside
 * `viewModelScope` on the IO dispatcher, so it's safely cancelled if the
 * user leaves the app.
 */
class TranslationViewModel(application: Application) : AndroidViewModel(application) {

    // Room database handle and ML Kit remote-model manager (for language packs).
    private val db = TranslationDatabase.get(application)
    private val modelManager = RemoteModelManager.getInstance()

    // ── Observable UI state (Compose `mutableStateOf` — UI recomposes on write) ──

    // Every language ML Kit supports, annotated with download state and size.
    var allLanguages by mutableStateOf<List<LanguageInfo>>(emptyList())
        private set
    // User-selected source/target languages (BCP-47 codes). English default source is a reasonable start.
    var sourceLang by mutableStateOf("en")
    var targetLang by mutableStateOf("")

    // Currently-picked PDF and the user-friendly file name shown in the UI.
    var selectedPdfUri by mutableStateOf<Uri?>(null)
        private set
    var selectedFileName by mutableStateOf("")
        private set

    // "direct" = extract text directly via PdfBox. "ocr" = render page + ML Kit vision.
    var extractionMethod by mutableStateOf("direct")
    // Translation-in-progress flag; disables buttons and shows the progress bar.
    var isTranslating by mutableStateOf(false)
        private set
    // Human-readable status ("Extracting text…", "Translating…", "Done!" or error).
    var translationProgress by mutableStateOf("")
        private set
    // 0..100 percentage shown in the progress bar.
    var translationPercent by mutableFloatStateOf(0f)
        private set
    // Output of the last successful translation (null until one finishes).
    var translatedPdfFile by mutableStateOf<File?>(null)
        private set

    // Rendered bitmaps of the translated PDF, shown one per page in PdfViewerScreen.
    var pdfPages by mutableStateOf<List<Bitmap>>(emptyList())
        private set

    // History list + current search query (wired to the History screen search field).
    var historyRecords by mutableStateOf<List<TranslationRecord>>(emptyList())
        private set
    var historySearch by mutableStateOf("")

    // Language codes that are currently being downloaded — drives the spinners.
    var downloadingLangs by mutableStateOf<Set<String>>(emptySet())
        private set

    // ── Update-check state (hybrid HTTP check + Play Store intent) ──
    // Flipped to true when version.json on GitHub reports a higher versionCode
    // than the installed build; drives the Settings "Update available" banner.
    var updateAvailable by mutableStateOf(false)
        private set
    // versionName of the newer release (for the banner subtitle). Null until checked.
    var latestVersionName by mutableStateOf<String?>(null)
        private set
    // Canonical web URL used as fallback if the Play Store app isn't installed.
    var updateUrl by mutableStateOf<String?>(null)
        private set

    init {
        // PdfBox-Android needs its resources (fonts, cmaps) initialised before first use.
        PDFBoxResourceLoader.init(application)
        // Populate `allLanguages` now so the Languages tab is never empty on first open.
        refreshLanguages()
    }

    // ── Language management ──────────────────────────────────────────────

    /**
     * Rebuild `allLanguages` from ML Kit's supported list, marking which ones
     * have been downloaded locally and estimating their on-disk size.
     */
    fun refreshLanguages() {
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { downloadedModels ->
                // Set of codes that have a local model already installed.
                val downloadedCodes = downloadedModels.map { it.language }.toSet()
                // Approximate MB-per-code found by scanning the app data dir.
                val modelSizes = getDownloadedModelSizes()
                // Produce one LanguageInfo per supported code, sorted by display name.
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

    /**
     * Walk the app's data directory looking for ML Kit translation model folders
     * and sum the file sizes inside each one. There's no official API for this, so
     * we heuristically find directories whose path contains both "translate" and "model".
     */
    private fun getDownloadedModelSizes(): Map<String, Float> {
        val sizes = mutableMapOf<String, Float>()
        val context = getApplication<Application>()

        // ML Kit stores its models under /data/data/<pkg>/... — start from the package root.
        val appDataDir = context.filesDir.parentFile ?: return sizes
        val candidates = mutableListOf<File>()

        // Breadth-limited walk to avoid pathological nesting; 6 levels is plenty here.
        appDataDir.walkTopDown()
            .maxDepth(6)
            .filter { it.isDirectory }
            .forEach { dir ->
                val pathLower = dir.absolutePath.lowercase()
                if (pathLower.contains("translate") && pathLower.contains("model")) {
                    candidates.add(dir)
                }
            }

        // For every candidate, list its children (one per language) and sum their file sizes.
        for (modelsDir in candidates) {
            modelsDir.listFiles()?.forEach { langDir ->
                if (langDir.isDirectory) {
                    val totalBytes = langDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    val mb = totalBytes / (1024f * 1024f)
                    if (mb > 0.01f) {
                        // The subfolder name is typically the BCP-47 language code.
                        val code = langDir.name.lowercase()
                        // Ignore anything that isn't actually a known ML Kit language.
                        if (TranslateLanguage.getAllLanguages().contains(code)) {
                            val existing = sizes[code] ?: 0f
                            // If multiple candidate dirs contain the same code, keep the larger estimate.
                            if (mb > existing) sizes[code] = mb
                        }
                    }
                }
            }
        }
        return sizes
    }

    /** Kick off an offline model download for the given language code. */
    fun downloadLanguage(code: String) {
        // Add to downloading set so the UI shows a spinner for this row.
        downloadingLangs = downloadingLangs + code
        val model = TranslateRemoteModel.Builder(code).build()
        // `DownloadConditions.Builder().build()` with no requirements = allow cellular data.
        val conditions = DownloadConditions.Builder().build()
        modelManager.download(model, conditions)
            .addOnSuccessListener {
                downloadingLangs = downloadingLangs - code
                refreshLanguages() // Refresh to flip the row from "Available" to "Downloaded".
            }
            .addOnFailureListener {
                // Remove from spinner set regardless of failure so the UI isn't stuck.
                downloadingLangs = downloadingLangs - code
            }
    }

    /** Delete an already-downloaded offline model to free up space. */
    fun deleteLanguage(code: String) {
        val model = TranslateRemoteModel.Builder(code).build()
        modelManager.deleteDownloadedModel(model)
            .addOnSuccessListener { refreshLanguages() }
    }

    // ── PDF selection ────────────────────────────────────────────────────

    /** Called by HomeFragment after the user picks a PDF via the SAF picker. */
    fun setPdfFile(uri: Uri, fileName: String) {
        selectedPdfUri = uri
        selectedFileName = fileName
        // Reset any prior translation so the "View / Download Translated" buttons hide.
        translatedPdfFile = null
        pdfPages = emptyList()
        // Also wipe the leftover "Done!" / error banner from the previous run so the
        // new file starts with a clean slate above the Translate button.
        translationProgress = ""
        translationPercent = 0f
    }

    // ── Translation pipeline ─────────────────────────────────────────────

    /**
     * The end-to-end translation flow:
     *   1. Extract text blocks from the PDF (direct or OCR).
     *   2. Merge adjacent blocks so whole paragraphs are translated together.
     *   3. Translate each block via ML Kit (offline model).
     *   4. Re-generate the PDF, overwriting the original text with the translation.
     *   5. Render the output for preview and persist a history record.
     */
    fun translate() {
        val uri = selectedPdfUri ?: return
        // Bail early on invalid combinations — no-op is fine, UI will stay enabled.
        if (sourceLang.isEmpty() || targetLang.isEmpty()) return
        if (sourceLang == targetLang) return

        // Flip the UI into "translating" state.
        isTranslating = true
        translationProgress = "Extracting text..."
        translationPercent = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                translationPercent = 5f
                // Step 1 — pick extraction strategy based on user choice.
                val blocks = if (extractionMethod == "direct") {
                    extractTextDirect(uri)
                } else {
                    extractTextOcr(uri)
                }
                translationPercent = 10f

                // If the PDF is empty/scanned with nothing detected, surface a friendly error.
                if (blocks.isEmpty()) {
                    translationProgress = "No text found in PDF."
                    translationPercent = 0f
                    isTranslating = false
                    return@launch
                }

                // Step 2 — glue nearby blocks together so we translate whole paragraphs.
                val mergedBlocks = mergeNearbyBlocks(blocks)

                // Step 3 — translate each merged block via ML Kit.
                translationProgress = "Translating..."
                val translatedBlocks = translateBlocks(mergedBlocks)

                // Step 4 — stamp translations back onto a copy of the original PDF.
                translationProgress = "Generating PDF..."
                translationPercent = 80f
                val outputFile = generateTranslatedPdf(uri, translatedBlocks)
                translationPercent = 95f

                // Step 5 — render pages + persist to history DB.
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
                // Any failure in the pipeline becomes a user-visible error message.
                translationProgress = "Error: ${e.message}"
                translationPercent = 0f
                isTranslating = false
            }
        }
    }

    // ── Direct text extraction (PdfBox) ──────────────────────────────────

    /**
     * Strategy 1: Use PdfBox to pull text directly out of the PDF's content streams.
     * Fastest, most accurate — but only works on PDFs that actually contain text
     * (not image scans).
     */
    private fun extractTextDirect(uri: Uri): List<TextBlock> {
        val context = getApplication<Application>()
        val blocks = mutableListOf<TextBlock>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            val doc = PDDocument.load(input)
            doc.use {
                // Process one page at a time so `writeString` sees only that page's positions.
                for (pageIndex in 0 until doc.numberOfPages) {
                    // Subclass the text stripper to intercept every run of positioned glyphs.
                    val stripper = object : PDFTextStripper() {
                        init {
                            startPage = pageIndex + 1
                            endPage = pageIndex + 1
                        }

                        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                            if (text.isBlank() || textPositions.isEmpty()) return
                            // First/last glyph give us the left and right edges of the run.
                            val firstPos = textPositions.first()
                            val lastPos = textPositions.last()

                            val x = firstPos.xDirAdj
                            val baseline = firstPos.yDirAdj
                            val height = firstPos.heightDir
                            val width = (lastPos.xDirAdj + lastPos.widthDirAdj) - x

                            // xDirAdj/yDirAdj report a baseline Y; we want the glyph's TOP edge.
                            // Approximate ascent with the glyph height for simplicity.
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
                            // Let the base stripper continue so its internal state stays consistent.
                            super.writeString(text, textPositions)
                        }
                    }
                    // Trigger the stripper — our overridden writeString fires for every run.
                    stripper.getText(doc)
                }
            }
        }
        return blocks
    }

    // ── OCR text extraction (ML Kit) ─────────────────────────────────────

    /**
     * Pick the best-suited ML Kit text recognizer for the source language.
     * Different scripts need different recognizers (CJK / Devanagari / Latin).
     */
    private fun getOcrRecognizer(): TextRecognizer {
        return when (sourceLang) {
            "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            // Devanagari script covers Hindi, Marathi, Nepali, Sanskrit.
            "hi", "mr", "ne", "sa" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            // Latin recognizer is the catch-all for every other supported script.
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    /**
     * Strategy 2: Render each PDF page to a bitmap at high resolution and run
     * ML Kit text recognition on it. Slower but works on scanned / image-only PDFs.
     */
    private suspend fun extractTextOcr(uri: Uri): List<TextBlock> {
        val context = getApplication<Application>()
        val blocks = mutableListOf<TextBlock>()
        val recognizer = getOcrRecognizer()

        // Open the PDF as a file descriptor — PdfRenderer needs it.
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return blocks
        val renderer = PdfRenderer(fd)

        for (pageIndex in 0 until renderer.pageCount) {
            val page = renderer.openPage(pageIndex)
            // Up-sample by 3x to give the OCR engine more pixels to work with.
            val scale = 3
            val bitmapW = page.width * scale
            val bitmapH = page.height * scale
            // `createBitmap` is the KTX extension (defaults to ARGB_8888) — preferred over the SDK call.
            val bitmap = createBitmap(bitmapW, bitmapH)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            // Capture the original page dimensions (in PDF points) so we can scale OCR results back.
            val pdfPageWidth = page.width.toFloat()
            val pdfPageHeight = page.height.toFloat()
            page.close()

            // Send the bitmap to ML Kit and await the recognizer's result list.
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()

            // Map from bitmap-pixel coords back down to PDF-point coords.
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
                        // OCR doesn't give us a font size — estimate from the block height.
                        fontSize = (box.height() * scaleY * 0.6f).coerceIn(6f, 48f),
                        pageIndex = pageIndex
                    )
                )
            }
            // Free the bitmap ASAP — rendering every page at 3x can spike memory hard.
            bitmap.recycle()
        }
        // Release native resources.
        renderer.close()
        fd.close()
        recognizer.close()
        return blocks
    }

    // ── Merge nearby blocks ────────────────────────────────────────────────

    /**
     * Combine text blocks that appear to belong to the same paragraph.
     * Translation quality is much higher on whole sentences than on fragments.
     */
    private fun mergeNearbyBlocks(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.isEmpty()) return blocks
        // Merging is always page-local — we never glue content across page boundaries.
        val byPage = blocks.groupBy { it.pageIndex }
        val result = mutableListOf<TextBlock>()

        for ((_, pageBlocks) in byPage) {
            // Sort top-to-bottom, left-to-right so iteration follows reading order.
            val sorted = pageBlocks.sortedWith(compareBy({ it.topY }, { it.x }))
            val merged = mutableListOf<TextBlock>()
            val used = BooleanArray(sorted.size)

            for (i in sorted.indices) {
                if (used[i]) continue
                var current = sorted[i]
                used[i] = true

                // Repeatedly sweep remaining blocks and fold in any neighbor that qualifies.
                // This handles chains like A-B-C where B only merges once A is merged in.
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

    /**
     * Heuristic for whether two blocks belong to the same paragraph:
     *   - vertical gap smaller than half a line height (they're on adjacent lines)
     *   - AND horizontal projections overlap (they're in the same column)
     */
    private fun shouldMerge(a: TextBlock, b: TextBlock): Boolean {
        // Vertical gap between the closer edges of the two blocks.
        val aBottom = a.topY + a.height
        val bBottom = b.topY + b.height
        val verticalGap = if (a.topY < b.topY) {
            b.topY - aBottom
        } else {
            a.topY - bBottom
        }
        val lineHeight = minOf(a.height, b.height)
        // Fail fast if the blocks are more than half a line apart vertically.
        if (verticalGap > lineHeight * 0.5f) return false

        // Horizontal overlap: positive = actually overlap, negative = gap between them.
        val aRight = a.x + a.width
        val bRight = b.x + b.width
        val horizontalOverlap = minOf(aRight, bRight) - maxOf(a.x, b.x)
        val minWidth = minOf(a.width, b.width)
        // Allow a small negative overlap (little horizontal gap) to still merge.
        return horizontalOverlap > -lineHeight && horizontalOverlap > minWidth * -0.3f
    }

    /**
     * Fuse two blocks into one: take the enclosing rectangle, keep the larger font
     * size, and join the text lines in top-to-bottom order separated by newline.
     */
    private fun mergeTwo(a: TextBlock, b: TextBlock): TextBlock {
        val minX = minOf(a.x, b.x)
        val minTopY = minOf(a.topY, b.topY)
        val maxBottom = maxOf(a.topY + a.height, b.topY + b.height)
        val maxRight = maxOf(a.x + a.width, b.x + b.width)
        // Whichever block is physically higher on the page gets its text first.
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

    /**
     * Translate every block sequentially with ML Kit, pairing each original block
     * with its translated string. Sequential (not parallel) because the translator
     * serialises internally anyway, and this keeps progress reporting monotonic.
     */
    private suspend fun translateBlocks(blocks: List<TextBlock>): List<Pair<TextBlock, String>> {
        // Configure and create a translator for this specific language pair.
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)
        // Ensure both offline models are present (no-op if already downloaded).
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions).await()

        val results = mutableListOf<Pair<TextBlock, String>>()
        for ((i, block) in blocks.withIndex()) {
            val translated = translator.translate(block.text).await()
            results.add(block to translated)
            // Translation spans 10% → 80% of the total progress bar.
            translationPercent = 10f + (70f * (i + 1) / blocks.size)
        }
        translator.close() // Release native resources held by ML Kit.
        return results
    }

    // ── PDF generation ───────────────────────────────────────────────────

    /**
     * Produce a copy of the original PDF where each block's area has been
     * painted white and the translated text stamped on top, using a font size
     * chosen to fit the available box.
     */
    private fun generateTranslatedPdf(
        originalUri: Uri,
        translatedBlocks: List<Pair<TextBlock, String>>
    ): File {
        val context = getApplication<Application>()
        val inputStream = context.contentResolver.openInputStream(originalUri)!!
        val doc = PDDocument.load(inputStream)

        // Group by page so we can open one content stream per page.
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
                // Convert from our "topY from page top" coord to PDF's "y from page bottom".
                val boxTopPdf = pageHeight - original.topY
                val boxBottomPdf = boxTopPdf - original.height

                // Opaque white cover with a small padding to erase the original text,
                // including any glyph overhang beyond the reported bounding box.
                val coverX = (original.x - 2f).coerceAtLeast(0f)
                val coverBottom = boxBottomPdf - 2f
                val coverW = (original.width + 4f).coerceAtMost(pageWidth - coverX)
                val coverH = original.height + 4f
                contentStream.setNonStrokingColor(1f, 1f, 1f)
                contentStream.addRect(coverX, coverBottom, coverW, coverH)
                contentStream.fill()

                if (translated.isBlank()) continue

                // Render the translated text through Android's own text engine — which
                // handles font fallback, BiDi, Arabic shaping, Devanagari clustering,
                // Thai line-breaking, CJK, and emoji — then embed the rendered bitmap.
                // This sidesteps every PdfBox-Android font-embedding limitation
                // (notably "OTF fonts do not have a glyf table" for CFF-based CJK faces).
                val bitmap = renderTextBitmap(
                    text = translated,
                    widthPts = original.width,
                    heightPts = original.height,
                    maxFontSizePts = original.fontSize,
                    targetLang = targetLang
                )

                if (bitmap != null) {
                    val image = LosslessFactory.createFromImage(doc, bitmap)
                    contentStream.drawImage(
                        image, original.x, boxBottomPdf, original.width, original.height
                    )
                    bitmap.recycle()
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

    /**
     * Render `text` into an opaque-white bitmap sized `widthPts × heightPts` (in PDF
     * points, 3x-oversampled to pixels for crispness at typical zoom levels).
     *
     * Uses `StaticLayout`, so all platform text features apply automatically:
     *   • Font fallback across every script Android ships a font for.
     *   • Unicode Bidirectional Algorithm (Arabic, Hebrew, mixed-direction lines).
     *   • Complex-text shaping (Arabic contextual forms, Devanagari clusters, Thai).
     *   • Proper line breaking for scripts without spaces (CJK, Thai).
     *
     * Font size is chosen by binary-searching the largest value in [4, max] whose
     * laid-out height fits inside the box. Returns `null` if the bitmap would be
     * too small to draw meaningfully.
     */
    private fun renderTextBitmap(
        text: String,
        widthPts: Float,
        heightPts: Float,
        maxFontSizePts: Float,
        targetLang: String
    ): Bitmap? {
        val scale = 3f // pts → px. 3x is a good crispness/size tradeoff.
        val widthPx = (widthPts * scale).toInt().coerceAtLeast(1)
        val heightPx = (heightPts * scale).toInt().coerceAtLeast(1)
        if (widthPx < 4 || heightPx < 4) return null

        val typeface = typefaceForLang(targetLang)
        val textDir = if (isRtlLang(targetLang)) TextDirectionHeuristics.FIRSTSTRONG_RTL
                      else TextDirectionHeuristics.FIRSTSTRONG_LTR
        val alignment = Layout.Alignment.ALIGN_NORMAL // Paragraph direction handles RTL.

        fun buildLayout(fontSizePx: Float): StaticLayout {
            val paint = TextPaint().apply {
                color = Color.BLACK
                textSize = fontSizePx
                this.typeface = typeface
                isAntiAlias = true
                isSubpixelText = true
            }
            return StaticLayout.Builder
                .obtain(text, 0, text.length, paint, widthPx)
                .setAlignment(alignment)
                .setTextDirection(textDir)
                .setLineSpacing(0f, 1.1f) // 10% extra line height — readable body text.
                .setIncludePad(false)
                .build()
        }

        val minFontSizePx = 4f * scale
        val maxFontSizePx = (maxFontSizePts * scale).coerceAtLeast(minFontSizePx)

        // Binary-search the largest font size whose laid-out height fits.
        var layout = buildLayout(maxFontSizePx)
        if (layout.height > heightPx) {
            var lo = minFontSizePx
            var hi = maxFontSizePx
            repeat(14) {
                val mid = (lo + hi) / 2f
                val candidate = buildLayout(mid)
                if (candidate.height <= heightPx) {
                    lo = mid
                    layout = candidate
                } else {
                    hi = mid
                }
            }
            // Guarantee we end on the last known-fitting layout, not a just-rejected one.
            if (layout.height > heightPx) layout = buildLayout(lo)
        }

        val bitmap = createBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        // Vertical center the laid-out text inside the box.
        val topOffset = ((heightPx - layout.height) / 2f).coerceAtLeast(0f)
        canvas.save()
        canvas.translate(0f, topOffset)
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    /**
     * Map a target language code to a `Typeface` that Android's fallback chain will
     * resolve correctly for that script. `Typeface.DEFAULT` is sufficient on
     * modern Android because the platform's font-matching substitutes script-
     * appropriate system fonts on its own — CJK, Arabic, Devanagari, Thai, etc.
     */
    private fun typefaceForLang(@Suppress("UNUSED_PARAMETER") lang: String): Typeface {
        return Typeface.DEFAULT
    }

    /** Languages whose base direction is right-to-left. */
    private fun isRtlLang(lang: String): Boolean {
        val code = lang.lowercase()
        return code == "ar" || code == "fa" || code == "ur" || code == "he" ||
                code == "yi" || code == "ps" || code == "sd" || code == "ug"
    }

    // ── PDF viewer ───────────────────────────────────────────────────────

    /**
     * Rasterize every page of `file` into a list of Bitmaps for the Compose viewer.
     * Done on the IO dispatcher since PdfRenderer does disk + native work.
     */
    fun renderPdf(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val bitmaps = mutableListOf<Bitmap>()
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                // 2x sampling for crisp rendering on typical phone screens.
                // KTX `createBitmap` defaults to ARGB_8888, which is exactly what we want.
                val bitmap = createBitmap(page.width * 2, page.height * 2)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            renderer.close()
            fd.close()
            // Publish to Compose state on the main thread so the UI observes the change.
            withContext(Dispatchers.Main) { pdfPages = bitmaps }
        }
    }

    // ── History ──────────────────────────────────────────────────────────

    /** Reload `historyRecords` — either the full list or a search-filtered one. */
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

    /** Remove a history entry and its on-disk PDF, then refresh the list. */
    fun deleteHistoryRecord(record: TranslationRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            db.translationDao().delete(record)
            File(record.outputPath).delete() // OK if the file was already gone.
            loadHistory()
        }
    }

    /** Re-open a previously generated translated PDF from history. */
    fun openTranslatedPdf(record: TranslationRecord) {
        val file = File(record.outputPath)
        // Only try to render if the file still exists on disk.
        if (file.exists()) {
            translatedPdfFile = file
            renderPdf(file)
        }
    }

    // ── Update check ─────────────────────────────────────────────────────

    /**
     * Fire-and-forget HTTP GET against the app's `version.json` on GitHub to see
     * whether a newer release exists on Google Play. If `latest_version_code` in
     * the response is strictly greater than `currentVersionCode`, flip the
     * `updateAvailable` state so the Settings screen shows the banner.
     *
     * The update itself is performed by the Play Store app via a `market://` intent
     * from the UI — this function only decides whether to surface the prompt.
     *
     * All exceptions (offline, DNS, 404, malformed JSON) are swallowed. The update
     * check is best-effort; a failed check must never break startup.
     */
    fun checkForAppUpdate(currentVersionCode: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // The "source of truth" — raw GitHub URL served fresh, no CDN caching
                // concerns beyond a minute or two.
                val url = URL(VERSION_JSON_URL)
                val connection = url.openConnection() as HttpURLConnection
                // Short timeouts so a flaky network never blocks the ViewModel.
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                // Real JSON parsing (not regex) — the schema has multiple fields now.
                val json = JSONObject(response)
                val latestCode = json.optInt("latest_version_code", -1)
                val latestName = json.optString("latest_version_name", "").ifBlank { null }
                val url2 = json.optString("update_url", "").ifBlank { null }

                if (latestCode > 0 && latestCode > currentVersionCode) {
                    // Publish to Compose state on the main thread so any open screen recomposes.
                    withContext(Dispatchers.Main) {
                        latestVersionName = latestName
                        updateUrl = url2
                        updateAvailable = true
                    }
                }
            } catch (_: Exception) {
                // Best effort — silently ignored (offline, 404, malformed JSON, …).
            }
        }
    }

    companion object {
        /** Raw URL of the `version.json` file on the app's GitHub repo `main` branch. */
        private const val VERSION_JSON_URL =
            "https://raw.githubusercontent.com/dariuszkrych/TranslatePDF_Android/main/version.json"

        /**
         * Convert a BCP-47 code to a human-readable language name using the JDK Locale API.
         * E.g. "en" -> "English", "es" -> "Spanish" (localized to the device locale).
         */
        fun languageDisplayName(code: String): String {
            return java.util.Locale.forLanguageTag(code).displayLanguage
        }
    }
}

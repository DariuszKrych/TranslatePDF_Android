package com.dariuszkrych.translatepdf

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dariuszkrych.translatepdf.data.TranslationDatabase
import com.dariuszkrych.translatepdf.data.TranslationRecord
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
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
 * One entry in the Languages screen list. Carries a BCP 47 code, its human
 * readable name, whether the offline ML Kit translation model is downloaded,
 * and the approximate on disk size in MB.
 */
data class LanguageInfo(
    val code: String,
    val name: String,
    val downloaded: Boolean,
    val sizeMb: Float = 0f
)

/**
 * A rectangular chunk of text extracted from a PDF page. Used by both extraction
 * strategies (direct text stripping and OCR).
 *
 * topY is measured from the TOP of the page so both strategies report the same
 * coordinate space. The PDF generator later converts this back to native PDF
 * coordinates, where the origin sits at the bottom left of the page.
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
 * The single shared ViewModel backing every screen (Home, Languages, History,
 * PdfViewer, Settings).
 *
 * Extends AndroidViewModel because we need an Application context for
 * ContentResolver, filesDir, SharedPreferences and ML Kit initialisation.
 *
 * All long running work (OCR, translation, PDF generation) runs inside
 * viewModelScope on the IO dispatcher, so it is safely cancelled if the
 * user leaves the app.
 */
class TranslationViewModel(application: Application) : AndroidViewModel(application) {

    // Room database handle and ML Kit remote model manager (for language packs).
    private val db = TranslationDatabase.get(application)
    private val modelManager = RemoteModelManager.getInstance()
    // Persistent per language size cache. ML Kit has no public API for model
    // size and its on disk layout is not stable across versions, so we record
    // the actual byte delta at download time and keep it here. Keys take the
    // form "size_<code>" mapped to a Float in MB.
    private val modelSizePrefs = application.getSharedPreferences("ml_model_sizes", Context.MODE_PRIVATE)

    // Observable UI state. Compose mutableStateOf triggers recomposition on every write.

    // Every language ML Kit supports, annotated with download state and size.
    var allLanguages by mutableStateOf<List<LanguageInfo>>(emptyList())
        private set
    // User selected source and target languages as BCP 47 codes. English is a
    // reasonable default source given the language coverage of ML Kit.
    var sourceLang by mutableStateOf("en")
    var targetLang by mutableStateOf("")

    // Currently picked PDF and the user friendly file name shown in the UI.
    var selectedPdfUri by mutableStateOf<Uri?>(null)
        private set
    var selectedFileName by mutableStateOf("")
        private set

    // "direct" extracts text directly via PdfBox. "ocr" renders each page to a
    // bitmap and runs ML Kit vision text recognition on it.
    var extractionMethod by mutableStateOf("direct")
    // Flag that a translation is currently running. Disables buttons and shows
    // the progress bar in HomeScreen.
    var isTranslating by mutableStateOf(false)
        private set
    // Human readable status ("Extracting text...", "Translating...", "Done!" or
    // an error message).
    var translationProgress by mutableStateOf("")
        private set
    // Percentage in the range 0 to 100 shown by the progress bar.
    var translationPercent by mutableFloatStateOf(0f)
        private set
    // Output of the last successful translation. Null until one finishes.
    var translatedPdfFile by mutableStateOf<File?>(null)
        private set

    // Rendered bitmaps of the translated PDF, one per page, displayed by PdfViewerScreen.
    var pdfPages by mutableStateOf<List<Bitmap>>(emptyList())
        private set

    // History list and current search query, wired to the History screen.
    var historyRecords by mutableStateOf<List<TranslationRecord>>(emptyList())
        private set
    var historySearch by mutableStateOf("")

    // Language codes currently being downloaded or queued for download. Drives
    // the per row spinners. Queued languages show a spinner too, even though
    // only one download runs at a time (see the download queue below).
    var downloadingLangs by mutableStateOf<Set<String>>(emptySet())
        private set

    // FIFO queue that serialises ML Kit model downloads. Concurrent downloads
    // would corrupt the byte delta measurement (bytesAfter for download A would
    // include bytes pulled by download B), so we run exactly one at a time.
    private val downloadQueue = ArrayDeque<String>()
    private var activeDownload: String? = null

    // Update check state for the HTTP check and direct APK install from GitHub.
    // updateAvailable flips to true when version.json on GitHub reports a higher
    // versionCode than the installed build. It drives the Settings banner.
    var updateAvailable by mutableStateOf(false)
        private set
    // versionName of the newer release, used in the banner subtitle. Null until checked.
    var latestVersionName by mutableStateOf<String?>(null)
        private set
    // Direct download URL of the new APK on GitHub (typically a Releases asset).
    var apkUrl by mutableStateOf<String?>(null)
        private set
    // True while the APK is being streamed from GitHub to the local cache.
    var updateDownloading by mutableStateOf(false)
        private set
    // Percentage in the range 0 to 100. Only meaningful while updateDownloading is true.
    var updateProgress by mutableStateOf(0)
        private set
    // Last download error (null on success or idle). Surfaces in the banner.
    var updateError by mutableStateOf<String?>(null)
        private set

    init {
        // PdfBox Android needs its resources (fonts, cmaps) initialised before first use.
        PDFBoxResourceLoader.init(application)
        // Populate allLanguages now so the Languages tab is never empty on first open.
        refreshLanguages()
    }

    // Language management

    /**
     * Rebuild allLanguages from the ML Kit supported list, marking which ones
     * have been downloaded locally and estimating their on disk size.
     */
    fun refreshLanguages() {
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { downloadedModels ->
                // Set of codes that have a local model already installed.
                val downloadedCodes = downloadedModels.map { it.language }.toSet()
                // Per language MB read from the cache we maintain at download time,
                // with a one time estimate for any language that was already on disk
                // before this build started recording deltas.
                val modelSizes = resolveModelSizes(downloadedCodes)
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
     * Return the recorded size for every currently downloaded language.
     *
     * Sizes are written at download time by diffing the ML Kit models directory
     * byte count before and after the download task completes. That is the only
     * reliable way to attribute bytes to a language. ML Kit ships no public API
     * for per model size, and its on disk layout (directory naming, nesting,
     * shared assets) varies enough across releases that any filename matching
     * heuristic ends up crediting the wrong language most of the time.
     *
     * For any code that exists on disk but has no recorded size (e.g. it was
     * downloaded by a previous build before tracking was added), distribute the
     * still unaccounted for bytes equally across those codes. That estimate is
     * then cached so it stays stable on subsequent refreshes.
     */
    private fun resolveModelSizes(downloadedCodes: Set<String>): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        if (downloadedCodes.isEmpty()) return result

        val missing = mutableListOf<String>()
        for (code in downloadedCodes) {
            val stored = modelSizePrefs.getFloat(prefKey(code), -1f)
            if (stored >= 0f) result[code] = stored else missing.add(code)
        }

        if (missing.isNotEmpty()) {
            val totalMb = getTotalModelsBytes() / (1024f * 1024f)
            val accountedMb = result.values.sum()
            val leftoverMb = (totalMb - accountedMb).coerceAtLeast(0f)
            val perMissingMb = if (missing.isNotEmpty()) leftoverMb / missing.size else 0f
            val editor = modelSizePrefs.edit()
            for (code in missing) {
                result[code] = perMissingMb
                // Cache so the estimate doesn't shift across refreshes.
                editor.putFloat(prefKey(code), perMissingMb)
            }
            editor.apply()
        }
        return result
    }

    /** Total bytes occupied by every ML Kit translate-models directory on disk. */
    private fun getTotalModelsBytes(): Long {
        val appDataDir = getApplication<Application>().filesDir.parentFile ?: return 0L
        var total = 0L
        appDataDir.walkTopDown()
            .maxDepth(6)
            .filter { it.isDirectory }
            .filter {
                val n = it.name.lowercase()
                n.contains("mlkit") && n.contains("translate") && n.contains("model")
            }
            .forEach { root ->
                total += root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        return total
    }

    private fun prefKey(code: String) = "size_${code.lowercase()}"

    /**
     * Enqueue an offline model download. Downloads run serially. Only one is
     * in flight at a time and subsequent taps are queued behind it. This keeps
     * the before and after byte delta clean so each language's size is recorded
     * against its own download and no other.
     */
    fun downloadLanguage(code: String) {
        // Ignore duplicate taps. The code is already active or already queued.
        if (code == activeDownload || code in downloadQueue) return
        // Spinner state reflects pending OR running so the UI reacts immediately.
        downloadingLangs = downloadingLangs + code
        downloadQueue.addLast(code)
        startNextDownloadIfIdle()
    }

    /** Pop the next queued download and run it. No op if one is already running. */
    private fun startNextDownloadIfIdle() {
        if (activeDownload != null) return
        val code = downloadQueue.removeFirstOrNull() ?: return
        activeDownload = code

        val model = TranslateRemoteModel.Builder(code).build()
        // DownloadConditions.Builder().build() with no requirements allows cellular data.
        val conditions = DownloadConditions.Builder().build()
        // Snapshot disk usage right before kicking off the download so the delta
        // attributed to this language excludes every prior download's bytes.
        val bytesBefore = getTotalModelsBytes()
        modelManager.download(model, conditions)
            .addOnSuccessListener {
                val bytesAfter = getTotalModelsBytes()
                val deltaMb = ((bytesAfter - bytesBefore).coerceAtLeast(0L)) / (1024f * 1024f)
                // First install wins. Record the delta only the first time we ever
                // measure one for this code. ML Kit retains shared tokenizer and
                // pivot metadata across delete and reinstall cycles, so the initial
                // install's delta is the honest on disk cost. Later reinstalls
                // measure a smaller than real marginal number. Freezing the first
                // value keeps the UI consistent across any sequence of delete and
                // reinstall operations.
                val alreadyRecorded = modelSizePrefs.contains(prefKey(code))
                if (!alreadyRecorded && deltaMb > 0.05f) {
                    modelSizePrefs.edit().putFloat(prefKey(code), deltaMb).apply()
                }
                // CJK and Devanagari OCR ship as a separate Play Services optional
                // module that ML Kit otherwise pulls lazily on the first
                // recognizer.process() call. That lazy fetch fails with "Waiting
                // for the text optional module to be downloaded" if the user runs
                // OCR before it completes (for example after they go offline).
                // Fetch it now while we know the device is online and the user
                // has just consented to a language install. The spinner stays up
                // until both downloads land so "Downloaded" only ever means the
                // translate model AND the OCR module are present.
                ensureOcrModuleForLang(code) {
                    downloadingLangs = downloadingLangs - code
                    activeDownload = null
                    refreshLanguages() // Flip the row from Available to Downloaded.
                    startNextDownloadIfIdle()
                }
            }
            .addOnFailureListener {
                // Clear both the spinner and the active slot so the queue advances
                // even when a single download fails.
                downloadingLangs = downloadingLangs - code
                activeDownload = null
                startNextDownloadIfIdle()
            }
    }

    /** Delete an already downloaded offline model to free up space. */
    fun deleteLanguage(code: String) {
        val model = TranslateRemoteModel.Builder(code).build()
        modelManager.deleteDownloadedModel(model)
            .addOnSuccessListener {
                // Keep the cached size entry. If the user redownloads later, the
                // measured delta will be smaller than the first install's because
                // shared ML Kit scaffolding stays on disk. Reusing the original
                // recorded size keeps the display stable across install and delete
                // cycles. See startNextDownloadIfIdle for the matching write guard.
                refreshLanguages()
            }
    }

    // PDF selection

    /** Called by HomeFragment after the user picks a PDF via the SAF picker. */
    fun setPdfFile(uri: Uri, fileName: String) {
        selectedPdfUri = uri
        selectedFileName = fileName
        // Reset any prior translation so the View and Download Translated buttons hide.
        translatedPdfFile = null
        pdfPages = emptyList()
        // Also wipe the leftover Done or error banner from the previous run so the
        // new file starts with a clean slate above the Translate button.
        translationProgress = ""
        translationPercent = 0f
    }

    // Translation pipeline

    /**
     * The end to end translation flow.
     *   1. Extract text blocks from the PDF (direct or OCR).
     *   2. Merge adjacent blocks so whole paragraphs are translated together.
     *   3. Translate each block via ML Kit using the offline model.
     *   4. Regenerate the PDF, overwriting the original text with the translation.
     *   5. Render the output for preview and persist a history record.
     */
    fun translate() {
        val uri = selectedPdfUri ?: return
        // Bail early on invalid combinations. A no op is fine here, the UI will stay enabled.
        if (sourceLang.isEmpty() || targetLang.isEmpty()) return
        if (sourceLang == targetLang) return

        // Flip the UI into the translating state.
        val app = getApplication<Application>()
        isTranslating = true
        translationProgress = app.getString(R.string.progress_extracting)
        translationPercent = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                translationPercent = 5f
                // Step 1. Pick the extraction strategy based on the user's choice.
                val blocks = if (extractionMethod == "direct") {
                    extractTextDirect(uri)
                } else {
                    extractTextOcr(uri)
                }
                translationPercent = 10f

                // If the PDF is empty or a scan with nothing detected, surface a friendly error.
                if (blocks.isEmpty()) {
                    translationProgress = app.getString(R.string.progress_no_text)
                    translationPercent = 0f
                    isTranslating = false
                    return@launch
                }

                // Step 2. Glue nearby blocks together so we translate whole paragraphs.
                val mergedBlocks = mergeNearbyBlocks(blocks)

                // Step 3. Translate each merged block via ML Kit.
                translationProgress = app.getString(R.string.progress_translating)
                val translatedBlocks = translateBlocks(mergedBlocks)

                // Step 4. Stamp translations back onto a copy of the original PDF.
                translationProgress = app.getString(R.string.progress_generating)
                translationPercent = 80f
                val outputFile = generateTranslatedPdf(uri, translatedBlocks)
                translationPercent = 95f

                // Step 5. Render pages and persist a row to the history database.
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
                translationProgress = app.getString(R.string.progress_done)
                isTranslating = false
            } catch (e: Exception) {
                // Any failure in the pipeline becomes a user visible error message.
                translationProgress = app.getString(R.string.progress_error_format, e.message ?: "")
                translationPercent = 0f
                isTranslating = false
            }
        }
    }

    // Direct text extraction (PdfBox)

    /**
     * Strategy 1. Use PdfBox to pull text directly out of the PDF's content
     * streams. Fastest and most accurate, but only works on PDFs that actually
     * contain text (not image scans).
     */
    private fun extractTextDirect(uri: Uri): List<TextBlock> {
        val context = getApplication<Application>()
        val blocks = mutableListOf<TextBlock>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            val doc = PDDocument.load(input)
            doc.use {
                // Process one page at a time so writeString sees only that page's positions.
                for (pageIndex in 0 until doc.numberOfPages) {
                    // Subclass the text stripper to intercept every run of positioned glyphs.
                    val stripper = object : PDFTextStripper() {
                        init {
                            startPage = pageIndex + 1
                            endPage = pageIndex + 1
                        }

                        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                            if (text.isBlank() || textPositions.isEmpty()) return
                            // First and last glyph give us the left and right edges of the run.
                            val firstPos = textPositions.first()
                            val lastPos = textPositions.last()

                            val x = firstPos.xDirAdj
                            val baseline = firstPos.yDirAdj
                            val height = firstPos.heightDir
                            val width = (lastPos.xDirAdj + lastPos.widthDirAdj) - x

                            // xDirAdj and yDirAdj report a baseline Y. We want the glyph's
                            // top edge, so approximate the ascent with the glyph height.
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
                    // Trigger the stripper. Our overridden writeString fires for every run.
                    stripper.getText(doc)
                }
            }
        }
        return blocks
    }

    // OCR text extraction (ML Kit)

    /**
     * Recognizer for scripts that ship as a separate Play Services optional
     * module (CJK, Devanagari). Returns null for languages whose script is
     * covered by the bundled Latin recognizer, since those need no extra download.
     */
    private fun optionalOcrRecognizer(code: String): TextRecognizer? {
        return when (code) {
            "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            // Devanagari script covers Hindi, Marathi, Nepali and Sanskrit.
            "hi", "mr", "ne", "sa" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            else -> null
        }
    }

    /**
     * Pick the best suited ML Kit text recognizer for the source language.
     * Different scripts need different recognizers (CJK, Devanagari, Latin).
     */
    private fun getOcrRecognizer(): TextRecognizer {
        return optionalOcrRecognizer(sourceLang)
            ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Prefetch the optional Play Services module that backs the script specific
     * OCR recognizer for code, then run onComplete. No op (and immediately
     * completes) for codes that use the bundled Latin recognizer.
     *
     * Failures are intentionally swallowed. If the module install fails (Play
     * Services unavailable, transient network), the translate model has already
     * landed and we do not want to leave the queue stuck. The lazy install in
     * extractTextOcr remains as a fallback the next time the user is online.
     */
    private fun ensureOcrModuleForLang(code: String, onComplete: () -> Unit) {
        val recognizer = optionalOcrRecognizer(code)
        if (recognizer == null) {
            onComplete()
            return
        }
        try {
            val request = ModuleInstallRequest.newBuilder().addApi(recognizer).build()
            ModuleInstall.getClient(getApplication())
                .installModules(request)
                .addOnCompleteListener {
                    recognizer.close()
                    onComplete()
                }
        } catch (_: Exception) {
            recognizer.close()
            onComplete()
        }
    }

    /**
     * Strategy 2. Render each PDF page to a bitmap at high resolution and run
     * ML Kit text recognition on it. Slower but works on scanned and image only PDFs.
     */
    private suspend fun extractTextOcr(uri: Uri): List<TextBlock> {
        val context = getApplication<Application>()
        val blocks = mutableListOf<TextBlock>()
        val recognizer = getOcrRecognizer()

        // The script specific text recognizer ships as an optional Play Services
        // module that is not present on a fresh install. Trigger the install
        // proactively and await completion so the first process() call does not
        // throw "Waiting for the text optional module to be downloaded" and force
        // the user to hit Translate a second time.
        try {
            translationProgress = context.getString(R.string.progress_preparing_ocr)
            val request = ModuleInstallRequest.newBuilder()
                .addApi(recognizer)
                .build()
            ModuleInstall.getClient(context).installModules(request).await()
            translationProgress = context.getString(R.string.progress_extracting)
        } catch (_: Exception) {
            // Non fatal. Fall through. If the recognizer still is not available
            // the outer try and catch in translate() surfaces the error message.
        }

        // Open the PDF as a file descriptor. PdfRenderer needs it.
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return blocks
        val renderer = PdfRenderer(fd)

        for (pageIndex in 0 until renderer.pageCount) {
            val page = renderer.openPage(pageIndex)
            // Upsample by 3x to give the OCR engine more pixels to work with.
            val scale = 3
            val bitmapW = page.width * scale
            val bitmapH = page.height * scale
            // createBitmap is the KTX extension (defaults to ARGB_8888) and is
            // preferred over the raw SDK call.
            val bitmap = createBitmap(bitmapW, bitmapH)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            // Capture the original page dimensions in PDF points so we can scale
            // OCR results back into PDF coordinate space.
            val pdfPageWidth = page.width.toFloat()
            val pdfPageHeight = page.height.toFloat()
            page.close()

            // Send the bitmap to ML Kit and await the recognizer's result list.
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()

            // Map from bitmap pixel coordinates back down to PDF point coordinates.
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
                        // OCR does not give us a font size, so estimate it from the block height.
                        fontSize = (box.height() * scaleY * 0.6f).coerceIn(6f, 48f),
                        pageIndex = pageIndex
                    )
                )
            }
            // Free the bitmap as soon as possible. Rendering every page at 3x can
            // spike memory hard on large documents.
            bitmap.recycle()
        }
        // Release native resources.
        renderer.close()
        fd.close()
        recognizer.close()
        return blocks
    }

    // Merge nearby blocks

    /**
     * Combine text blocks that appear to belong to the same paragraph.
     * Translation quality is much higher on whole sentences than on fragments.
     */
    private fun mergeNearbyBlocks(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.isEmpty()) return blocks
        // Merging is always page local. We never glue content across page boundaries.
        val byPage = blocks.groupBy { it.pageIndex }
        val result = mutableListOf<TextBlock>()

        for ((_, pageBlocks) in byPage) {
            // Sort top to bottom and left to right so iteration follows reading order.
            val sorted = pageBlocks.sortedWith(compareBy({ it.topY }, { it.x }))
            val merged = mutableListOf<TextBlock>()
            val used = BooleanArray(sorted.size)

            for (i in sorted.indices) {
                if (used[i]) continue
                var current = sorted[i]
                used[i] = true

                // Repeatedly sweep remaining blocks and fold in any neighbor that qualifies.
                // This handles chains like A, B, C where B only merges once A is merged in.
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
     * Heuristic for whether two blocks belong to the same paragraph.
     *   1. Vertical gap smaller than half a line height. They sit on adjacent lines.
     *   2. AND horizontal projections overlap. They sit in the same column.
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

        // Horizontal overlap. Positive means actual overlap, negative means a gap.
        val aRight = a.x + a.width
        val bRight = b.x + b.width
        val horizontalOverlap = minOf(aRight, bRight) - maxOf(a.x, b.x)
        val minWidth = minOf(a.width, b.width)
        // Allow a small negative overlap (a tiny horizontal gap) to still merge.
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

    // Translation

    /**
     * Translate every block sequentially with ML Kit, pairing each original block
     * with its translated string. Sequential rather than parallel because the
     * translator serialises internally anyway, and this keeps progress reporting
     * monotonic.
     */
    private suspend fun translateBlocks(blocks: List<TextBlock>): List<Pair<TextBlock, String>> {
        // Configure and create a translator for this specific language pair.
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)
        // Ensure both offline models are present (no op if already downloaded).
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions).await()

        val results = mutableListOf<Pair<TextBlock, String>>()
        for ((i, block) in blocks.withIndex()) {
            val translated = translator.translate(block.text).await()
            results.add(block to translated)
            // Translation spans 10 to 80 percent of the total progress bar.
            translationPercent = 10f + (70f * (i + 1) / blocks.size)
        }
        translator.close() // Release native resources held by ML Kit.
        return results
    }

    // PDF generation

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
                // Convert from our "topY measured from page top" coordinate to the
                // PDF native "y measured from page bottom" coordinate.
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

                // Render the translated text through Android's own text engine, which
                // handles font fallback, BiDi, Arabic shaping, Devanagari clustering,
                // Thai line breaking, CJK and emoji. The rendered bitmap is then
                // embedded into the PDF. This sidesteps every PdfBox Android font
                // embedding limitation (notably "OTF fonts do not have a glyf table"
                // for CFF based CJK faces).
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
     * Render text into an opaque white bitmap sized widthPts by heightPts in PDF
     * points, 3x oversampled to pixels for crispness at typical zoom levels.
     *
     * Uses StaticLayout, so all platform text features apply automatically.
     *   1. Font fallback across every script Android ships a font for.
     *   2. Unicode Bidirectional Algorithm (Arabic, Hebrew, mixed direction lines).
     *   3. Complex text shaping (Arabic contextual forms, Devanagari clusters, Thai).
     *   4. Proper line breaking for scripts without spaces (CJK, Thai).
     *
     * Font size is chosen by binary searching the largest value in the range
     * [4, max] whose laid out height fits inside the box. Returns null if the
     * bitmap would be too small to draw meaningfully.
     */
    private fun renderTextBitmap(
        text: String,
        widthPts: Float,
        heightPts: Float,
        maxFontSizePts: Float,
        targetLang: String
    ): Bitmap? {
        val scale = 3f // Points to pixels. 3x is a good crispness vs. size tradeoff.
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
                .setLineSpacing(0f, 1.1f) // 10 percent extra line height for readable body text.
                .setIncludePad(false)
                .build()
        }

        val minFontSizePx = 4f * scale
        val maxFontSizePx = (maxFontSizePts * scale).coerceAtLeast(minFontSizePx)

        // Binary search for the largest font size whose laid out height fits.
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
            // Guarantee we end on the last known fitting layout, not a just rejected one.
            if (layout.height > heightPx) layout = buildLayout(lo)
        }

        val bitmap = createBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        // Vertically center the laid out text inside the box.
        val topOffset = ((heightPx - layout.height) / 2f).coerceAtLeast(0f)
        canvas.save()
        canvas.translate(0f, topOffset)
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    /**
     * Map a target language code to a Typeface that Android's fallback chain will
     * resolve correctly for that script. Typeface.DEFAULT is sufficient on modern
     * Android because the platform's font matching substitutes script appropriate
     * system fonts on its own (CJK, Arabic, Devanagari, Thai and others).
     */
    private fun typefaceForLang(@Suppress("UNUSED_PARAMETER") lang: String): Typeface {
        return Typeface.DEFAULT
    }

    /** Languages whose base direction is right to left. */
    private fun isRtlLang(lang: String): Boolean {
        val code = lang.lowercase()
        return code == "ar" || code == "fa" || code == "ur" || code == "he" ||
                code == "yi" || code == "ps" || code == "sd" || code == "ug"
    }

    // PDF viewer

    /**
     * Rasterize every page of file into a list of Bitmaps for the Compose viewer.
     * Done on the IO dispatcher because PdfRenderer does disk and native work.
     */
    fun renderPdf(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val bitmaps = mutableListOf<Bitmap>()
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                // 2x sampling for crisp rendering on typical phone screens.
                // KTX createBitmap defaults to ARGB_8888, which is exactly what we want.
                val bitmap = createBitmap(page.width * 2, page.height * 2)
                // PdfRenderer only paints drawn content. Pixels the PDF did not touch
                // stay transparent and let the themed Surface or Card behind the preview
                // bleed through, making the page look dark grey or light grey instead
                // of white. Prefill so every page is opaque white like a real PDF page.
                bitmap.eraseColor(Color.WHITE)
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

    // History

    /** Reload historyRecords with either the full list or a search filtered one. */
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

    /** Remove a history entry and its on disk PDF, then refresh the list. */
    fun deleteHistoryRecord(record: TranslationRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            db.translationDao().delete(record)
            File(record.outputPath).delete() // Returns false if already gone, which is fine.
            loadHistory()
        }
    }

    /** Reopen a previously generated translated PDF from history. */
    fun openTranslatedPdf(record: TranslationRecord) {
        val file = File(record.outputPath)
        // Only try to render if the file still exists on disk.
        if (file.exists()) {
            translatedPdfFile = file
            // Drop the previously rendered bitmaps synchronously so the viewer
            // shows its loading spinner instead of flashing the last record's
            // pages while the newly selected file rasterizes on IO.
            pdfPages = emptyList()
            renderPdf(file)
        }
    }

    // Update check + direct GitHub install

    /**
     * Fire and forget HTTP GET against the app's version.json on GitHub to see
     * whether a newer release exists. If latest_version_code in the response is
     * strictly greater than currentVersionCode, flip the updateAvailable state
     * so the Settings screen shows the banner.
     *
     * All exceptions (offline, DNS, 404, malformed JSON) are swallowed. The update
     * check is best effort. A failed check must never break startup.
     */
    fun checkForAppUpdate(currentVersionCode: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL(VERSION_JSON_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(response)
                val latestCode = json.optInt("latest_version_code", -1)
                val latestName = json.optString("latest_version_name", "").ifBlank { null }
                val downloadUrl = json.optString("apk_url", "").ifBlank { null }

                if (latestCode > 0 && latestCode > currentVersionCode && downloadUrl != null) {
                    withContext(Dispatchers.Main) {
                        latestVersionName = latestName
                        apkUrl = downloadUrl
                        updateAvailable = true
                    }
                }
            } catch (_: Exception) {
                // Best effort. Silently ignored (offline, 404, malformed JSON, etc.).
            }
        }
    }

    /**
     * Download the APK pointed at by apkUrl to the app's cache directory, then
     * hand it to the system package installer. On Android 8 and newer the user
     * may first be sent to the "Install unknown apps" settings page if they have
     * not granted the app permission to install packages. They return here, tap
     * Update again, and the cached APK is reused.
     */
    fun downloadAndInstallUpdate(context: Context) {
        val downloadUrl = apkUrl ?: return
        if (updateDownloading) return

        // On Android 8 and newer, sideloading requires the user to grant
        // REQUEST_INSTALL_PACKAGES from system settings. If we do not have it,
        // bounce the user to the relevant settings page first.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return
        }

        // Reset banner state and flag the download as in flight.
        updateDownloading = true
        updateProgress = 0
        updateError = null

        // Capture the application context so we do not retain the calling
        // Activity past its lifecycle while running on the IO dispatcher.
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Follow redirects so a Releases asset URL that redirects to a
                // CDN edge resolves cleanly.
                val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                val total = connection.contentLengthLong

                // App private cache subdirectory for the downloaded APK. Cleared
                // here on every fresh attempt to avoid resuming a stale partial.
                val cacheDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(cacheDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                // Stream the body to disk in 16 KB chunks. Only post a progress
                // update on integer percent changes to avoid flooding the main
                // thread with redundant state writes.
                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastReported = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            downloaded += n
                            if (total > 0) {
                                val pct = ((downloaded * 100) / total).toInt()
                                if (pct != lastReported) {
                                    lastReported = pct
                                    withContext(Dispatchers.Main) { updateProgress = pct }
                                }
                            }
                        }
                    }
                }
                connection.disconnect()

                withContext(Dispatchers.Main) {
                    updateProgress = 100
                    launchInstaller(appContext, apkFile)
                    updateDownloading = false
                }
            } catch (e: Exception) {
                // Surface the failure in the banner. Any exception (timeout, IO,
                // SSL, redirect loop) ends up here.
                withContext(Dispatchers.Main) {
                    updateDownloading = false
                    updateError = e.message ?: "Download failed"
                }
            }
        }
    }

    /**
     * Hand the freshly downloaded APK to the system package installer via an
     * ACTION_VIEW intent. The file is exposed through our FileProvider authority
     * so the installer (which runs in another process) can read it without
     * tripping FileUriExposedException.
     */
    private fun launchInstaller(context: Context, apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    companion object {
        /** Raw URL of the version.json file on the app's GitHub repo main branch. */
        private const val VERSION_JSON_URL =
            "https://raw.githubusercontent.com/dariuszkrych/TranslatePDF_Android/main/version.json"

        /**
         * Convert a BCP 47 code to a human readable language name using the JDK
         * Locale API. For example "en" becomes "English" and "es" becomes
         * "Spanish", localised to the device locale.
         */
        fun languageDisplayName(code: String): String {
            return java.util.Locale.forLanguageTag(code).displayLanguage
        }
    }
}

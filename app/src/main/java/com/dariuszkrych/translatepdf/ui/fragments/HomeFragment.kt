package com.dariuszkrych.translatepdf.ui.fragments

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.dariuszkrych.translatepdf.MainActivity
import com.dariuszkrych.translatepdf.R
import com.dariuszkrych.translatepdf.TranslationViewModel
import com.dariuszkrych.translatepdf.ui.screens.HomeScreen
import com.dariuszkrych.translatepdf.ui.theme.TranslatePDFTheme
import java.io.File

/**
 * Primary landing fragment — shown on the first ViewPager tab.
 *
 * Lets the user pick a PDF, choose extraction method, start the translation,
 * and then view or download the result. Actual UI is a Compose [HomeScreen]
 * hosted inside a ComposeView; this class is the Android-framework glue.
 */
class HomeFragment : Fragment() {

    // Scoped to the Activity so state (selected PDF, progress, translated file) is
    // shared across every tab and survives Fragment recreations.
    private val viewModel: TranslationViewModel by activityViewModels()

    /**
     * Registered Activity Result launcher for the Storage Access Framework picker.
     * Using `OpenDocument` (rather than a raw intent) gives us a persistable URI
     * plus automatic permission handling.
     */
    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        // Keep the URI permission across process death / reboot so history replay works.
        requireContext().contentResolver.takePersistableUriPermission(
            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        // Resolve the user-facing file name from the content provider and hand both to the VM.
        val name = getFileName(uri)
        viewModel.setPdfFile(uri, name)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Build a ComposeView that renders the HomeScreen. Every callback is forwarded
        // to the ViewModel or to the hosting Activity (for navigation side-effects).
        return ComposeView(requireContext()).apply {
            setContent {
                TranslatePDFTheme {
                    HomeScreen(
                        fileName = viewModel.selectedFileName,
                        extractionMethod = viewModel.extractionMethod,
                        isTranslating = viewModel.isTranslating,
                        translationProgress = viewModel.translationProgress,
                        translationPercent = viewModel.translationPercent,
                        sourceLang = viewModel.sourceLang,
                        targetLang = viewModel.targetLang,
                        hasPdf = viewModel.selectedPdfUri != null,
                        // Launch SAF picker with MIME filter so only PDFs are selectable.
                        onPickPdf = { pickPdf.launch(arrayOf("application/pdf")) },
                        onExtractionMethodChanged = { viewModel.extractionMethod = it },
                        onTranslate = { viewModel.translate() },
                        // Ask the hosting Activity to swap in the PDF viewer fragment.
                        onViewTranslated = { (requireActivity() as MainActivity).showPdfViewer() },
                        onDownloadTranslated = { downloadTranslatedPdf() },
                        hasTranslatedPdf = viewModel.translatedPdfFile != null
                    )
                }
            }
        }
    }

    /**
     * Copy the translated PDF from app-private storage into the device's Downloads folder.
     * Uses MediaStore on Android Q+ (scoped storage), legacy File API below that.
     */
    private fun downloadTranslatedPdf() {
        val file = viewModel.translatedPdfFile ?: return
        val ctx = requireContext()
        val outputName = "translated_${viewModel.selectedFileName}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped-storage path: let MediaStore allocate the file in Downloads.
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outputName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                // Stream the file bytes into the newly-created MediaStore entry.
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(ctx, getString(R.string.toast_saved_to_downloads), Toast.LENGTH_SHORT).show()
            }
        } else {
            // Legacy path (pre-Q): write directly to the public Downloads directory.
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dest = File(downloadsDir, outputName)
            file.copyTo(dest, overwrite = true)
            Toast.makeText(ctx, getString(R.string.toast_saved_to_downloads), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Look up the display name for a content-URI. Used so the UI shows
     * "myfile.pdf" instead of an opaque content:// path.
     */
    private fun getFileName(uri: android.net.Uri): String {
        var name = "document.pdf" // Reasonable default if the provider gives us nothing.
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}

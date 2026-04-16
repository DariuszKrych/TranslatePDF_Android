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
import com.dariuszkrych.translatepdf.TranslationViewModel
import com.dariuszkrych.translatepdf.ui.screens.HomeScreen
import com.dariuszkrych.translatepdf.ui.theme.TranslatePDFTheme
import java.io.File

class HomeFragment : Fragment() {

    private val viewModel: TranslationViewModel by activityViewModels()

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        requireContext().contentResolver.takePersistableUriPermission(
            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val name = getFileName(uri)
        viewModel.setPdfFile(uri, name)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
                        onPickPdf = { pickPdf.launch(arrayOf("application/pdf")) },
                        onExtractionMethodChanged = { viewModel.extractionMethod = it },
                        onTranslate = { viewModel.translate() },
                        onViewTranslated = { (requireActivity() as MainActivity).showPdfViewer() },
                        onDownloadTranslated = { downloadTranslatedPdf() },
                        hasTranslatedPdf = viewModel.translatedPdfFile != null
                    )
                }
            }
        }
    }

    private fun downloadTranslatedPdf() {
        val file = viewModel.translatedPdfFile ?: return
        val ctx = requireContext()
        val outputName = "translated_${viewModel.selectedFileName}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outputName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(ctx, "Saved to Downloads", Toast.LENGTH_SHORT).show()
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dest = File(downloadsDir, outputName)
            file.copyTo(dest, overwrite = true)
            Toast.makeText(ctx, "Saved to Downloads", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: android.net.Uri): String {
        var name = "document.pdf"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}

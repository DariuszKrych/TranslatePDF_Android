package com.dariuszkrych.translatepdf.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.dariuszkrych.translatepdf.MainActivity
import com.dariuszkrych.translatepdf.TranslationViewModel
import com.dariuszkrych.translatepdf.ui.screens.PdfViewerScreen
import com.dariuszkrych.translatepdf.ui.theme.TranslatePDFTheme

/**
 * Overlay fragment that shows the translated PDF as a scrollable list of page
 * bitmaps. Shown in the `settingsContainer` FrameLayout by MainActivity.
 *
 * Provides a Back button (hides the overlay) and a Share button (fires an
 * ACTION_SEND intent through a FileProvider so other apps can read the PDF).
 */
class PdfViewerFragment : Fragment() {

    // Activity-scoped VM so we reuse the same rendered bitmaps and file handle.
    private val viewModel: TranslationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                TranslatePDFTheme {
                    PdfViewerScreen(
                        pages = viewModel.pdfPages,
                        onShare = { sharePdf() },
                        // Delegate back-press to the activity so the overlay hides correctly.
                        onBack = { (requireActivity() as MainActivity).hidePdfViewer() }
                    )
                }
            }
        }
    }

    /**
     * Launch a system "share" chooser for the translated PDF.
     *
     * We cannot hand the raw File path to other apps (scoped storage, FileUriExposedException)
     * so we expose the file via our FileProvider authority declared in the manifest,
     * then grant temporary read permission to the receiving app.
     */
    private fun sharePdf() {
        val file = viewModel.translatedPdfFile ?: return
        // content:// URI backed by the FileProvider entry in AndroidManifest.xml.
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        // Build an implicit ACTION_SEND intent with the URI as the payload.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Temp grant for the chosen app.
        }
        // `createChooser` forces the system picker even if a default handler is set.
        startActivity(Intent.createChooser(intent, "Share Translated PDF"))
    }
}

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

class PdfViewerFragment : Fragment() {

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
                        onBack = { (requireActivity() as MainActivity).hidePdfViewer() }
                    )
                }
            }
        }
    }

    private fun sharePdf() {
        val file = viewModel.translatedPdfFile ?: return
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Translated PDF"))
    }
}

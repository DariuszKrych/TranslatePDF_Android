package com.dariuszkrych.translatepdf.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.dariuszkrych.translatepdf.MainActivity
import com.dariuszkrych.translatepdf.TranslationViewModel
import com.dariuszkrych.translatepdf.ui.screens.HistoryScreen
import com.dariuszkrych.translatepdf.ui.theme.TranslatePDFTheme

class HistoryFragment : Fragment() {

    private val viewModel: TranslationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                TranslatePDFTheme {
                    HistoryScreen(
                        records = viewModel.historyRecords,
                        searchQuery = viewModel.historySearch,
                        onSearchChanged = {
                            viewModel.historySearch = it
                            viewModel.loadHistory()
                        },
                        onRecordClick = {
                            viewModel.openTranslatedPdf(it)
                            (requireActivity() as MainActivity).showPdfViewer()
                        },
                        onRecordDelete = { viewModel.deleteHistoryRecord(it) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadHistory()
    }
}

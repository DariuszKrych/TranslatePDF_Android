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

/**
 * Third ViewPager tab — displays previously completed translations from the
 * Room database, with search, delete, and tap-to-reopen support.
 */
class HistoryFragment : Fragment() {

    // Activity-scoped VM so we see the same history/search state as the rest of the app.
    private val viewModel: TranslationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Host the Compose HistoryScreen and wire each callback to the ViewModel.
        return ComposeView(requireContext()).apply {
            setContent {
                TranslatePDFTheme {
                    HistoryScreen(
                        records = viewModel.historyRecords,
                        searchQuery = viewModel.historySearch,
                        // Each keystroke updates the query and triggers a fresh DB lookup.
                        onSearchChanged = {
                            viewModel.historySearch = it
                            viewModel.loadHistory()
                        },
                        // Tapping a row re-renders that PDF and opens the viewer overlay.
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

    /**
     * Reload the history list every time the tab becomes visible so newly
     * completed translations show up immediately without navigating away and back.
     */
    override fun onResume() {
        super.onResume()
        viewModel.loadHistory()
    }
}

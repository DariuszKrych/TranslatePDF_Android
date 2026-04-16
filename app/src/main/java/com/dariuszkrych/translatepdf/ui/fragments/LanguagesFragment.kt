package com.dariuszkrych.translatepdf.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.dariuszkrych.translatepdf.TranslationViewModel
import com.dariuszkrych.translatepdf.ui.screens.LanguagesScreen
import com.dariuszkrych.translatepdf.ui.theme.TranslatePDFTheme

class LanguagesFragment : Fragment() {

    private val viewModel: TranslationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                TranslatePDFTheme {
                    LanguagesScreen(
                        languages = viewModel.allLanguages,
                        sourceLang = viewModel.sourceLang,
                        targetLang = viewModel.targetLang,
                        downloadingLangs = viewModel.downloadingLangs,
                        onSourceSelected = { viewModel.sourceLang = it },
                        onTargetSelected = { viewModel.targetLang = it },
                        onDownload = { viewModel.downloadLanguage(it) },
                        onDelete = { viewModel.deleteLanguage(it) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLanguages()
    }
}

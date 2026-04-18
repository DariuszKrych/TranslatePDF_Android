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

/**
 * Second ViewPager tab — lets the user pick source/target languages and
 * download / delete offline ML Kit translation models.
 */
class LanguagesFragment : Fragment() {

    // Same shared VM instance as Home/History — selections survive tab switches.
    private val viewModel: TranslationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Compose UI: the fragment's only job is to map VM state/actions to the screen.
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

    /**
     * Re-scan the downloaded-model set each time the tab is shown so size estimates
     * and download flags stay fresh (they can change while downloads finish in the bg).
     */
    override fun onResume() {
        super.onResume()
        viewModel.refreshLanguages()
    }
}

package com.dariuszkrych.translatepdf.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.dariuszkrych.translatepdf.ui.screens.LanguagesScreen
import com.dariuszkrych.translatepdf.ui.theme.TranslatePDFTheme

class LanguagesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                TranslatePDFTheme {
                    LanguagesScreen()
                }
            }
        }
    }
}

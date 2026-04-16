package com.dariuszkrych.translatepdf.ui.fragments

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.dariuszkrych.translatepdf.ui.screens.SettingsScreen
import com.dariuszkrych.translatepdf.ui.theme.ThemeState
import com.dariuszkrych.translatepdf.ui.theme.TranslatePDFTheme

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val currentTheme = remember {
                    mutableStateOf(prefs.getString("theme", "system") ?: "system")
                }

                TranslatePDFTheme {
                    SettingsScreen(
                        currentTheme = currentTheme.value,
                        onThemeSelected = { theme ->
                            prefs.edit().putString("theme", theme).apply()
                            currentTheme.value = theme

                            val nightMode = when (theme) {
                                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                            }
                            AppCompatDelegate.setDefaultNightMode(nightMode)

                            ThemeState.isDark = when (theme) {
                                "dark" -> true
                                "light" -> false
                                else -> {
                                    val uiMode = resources.configuration.uiMode and
                                            Configuration.UI_MODE_NIGHT_MASK
                                    uiMode == Configuration.UI_MODE_NIGHT_YES
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

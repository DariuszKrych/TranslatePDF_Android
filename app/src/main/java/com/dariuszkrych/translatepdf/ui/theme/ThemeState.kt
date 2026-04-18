package com.dariuszkrych.translatepdf.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global, Compose-observable holder for the current "dark mode" flag.
 *
 * Why a singleton: both the XML-based chrome (toolbar, bottom nav, status bar) in
 * MainActivity AND the Compose subtree need to react to theme changes in lockstep.
 * Writing to [isDark] triggers recomposition anywhere it is read from.
 */
object ThemeState {
    // True when the app should render in dark mode. Updated by MainActivity on config
    // change and by SettingsFragment when the user picks a theme.
    var isDark by mutableStateOf(false)
}

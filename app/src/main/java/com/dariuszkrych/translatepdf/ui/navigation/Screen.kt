package com.dariuszkrych.translatepdf.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Languages : Screen("languages")
    object History : Screen("history")
    object Settings : Screen("settings")
}

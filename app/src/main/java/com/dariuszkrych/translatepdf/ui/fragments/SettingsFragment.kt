package com.dariuszkrych.translatepdf.ui.fragments

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.dariuszkrych.translatepdf.TranslationViewModel
import com.dariuszkrych.translatepdf.ui.screens.SettingsScreen
import com.dariuszkrych.translatepdf.ui.theme.ThemeState
import com.dariuszkrych.translatepdf.ui.theme.TranslatePDFTheme

/**
 * Overlay fragment opened from the toolbar gear icon. Lets the user choose a
 * theme (System / Dark / Light) and open the Play Store review page.
 *
 * Persistence uses SharedPreferences (a tiny key/value store on disk) so the
 * chosen theme survives restarts — SQLite would be overkill for one string.
 */
class SettingsFragment : Fragment() {
    // Shared across fragments — same instance MainActivity uses to trigger the
    // update check at startup. Reading its Compose state here means the banner
    // flips on automatically if the HTTP request finishes while this screen is open.
    private val viewModel: TranslationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                // Read the currently saved theme once; `remember` keeps it across recompositions.
                val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val currentTheme = remember {
                    mutableStateOf(prefs.getString("theme", "system") ?: "system")
                }

                TranslatePDFTheme {
                    // Reading these fields inside the composable lets Compose's snapshot
                    // system re-render the banner when `checkForAppUpdate` flips them.
                    SettingsScreen(
                        currentTheme = currentTheme.value,
                        updateAvailable = viewModel.updateAvailable,
                        latestVersionName = viewModel.latestVersionName,
                        onUpdateClick = {
                            // Prefer the Play Store app (market://) — Play handles actual
                            // update install. Fall back to the canonical web URL (or the
                            // update_url supplied by version.json) if Play isn't installed.
                            val packageName = requireContext().packageName
                            val fallbackUrl = viewModel.updateUrl
                                ?: "https://play.google.com/store/apps/details?id=$packageName"
                            val marketIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$packageName")
                            ).apply { setPackage("com.android.vending") }
                            try {
                                startActivity(marketIntent)
                            } catch (_: ActivityNotFoundException) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
                            }
                        },
                        onThemeSelected = { theme ->
                            // 1) Persist the choice so next launch opens with it already applied.
                            prefs.edit().putString("theme", theme).apply()
                            // 2) Update the local Compose state so the button selection re-renders.
                            currentTheme.value = theme

                            // 3) Tell AppCompat to flip night mode at runtime — updates the activity
                            //    and any downstream resource lookups immediately.
                            val nightMode = when (theme) {
                                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                            }
                            AppCompatDelegate.setDefaultNightMode(nightMode)

                            // 4) Keep the Compose theme flag in sync. For "system" we peek at the
                            //    current uiMode to decide true/false right now.
                            ThemeState.isDark = when (theme) {
                                "dark" -> true
                                "light" -> false
                                else -> {
                                    val uiMode = resources.configuration.uiMode and
                                            Configuration.UI_MODE_NIGHT_MASK
                                    uiMode == Configuration.UI_MODE_NIGHT_YES
                                }
                            }
                        },
                        onReviewClick = {
                            // Try to open the Play Store app directly; if it isn't installed
                            // (emulator, sideload), fall back to the https:// URL in a browser.
                            val packageName = requireContext().packageName
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                            } catch (_: Exception) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                            }
                        }
                    )
                }
            }
        }
    }
}

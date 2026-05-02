package com.dariuszkrych.translatepdf

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.dariuszkrych.translatepdf.ui.fragments.HistoryFragment
import com.dariuszkrych.translatepdf.ui.fragments.HomeFragment
import com.dariuszkrych.translatepdf.ui.fragments.LanguagesFragment
import com.dariuszkrych.translatepdf.ui.fragments.PdfViewerFragment
import com.dariuszkrych.translatepdf.ui.fragments.SettingsFragment
import com.dariuszkrych.translatepdf.ui.theme.ThemeState
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * The app's single Activity. Hosts a ViewPager2 with three main tabs
 * (Home / Languages / History) and an overlay FrameLayout for Settings and the PDF viewer.
 *
 * Everything else in the app is a Fragment hosted inside this activity.
 */
class MainActivity : AppCompatActivity() {

    // Horizontal swiper that shows the three primary fragments.
    private lateinit var viewPager: ViewPager2
    // Bottom tab bar kept in sync with the ViewPager page.
    private lateinit var bottomNavigation: BottomNavigationView
    // Thin colored strip drawn on top of the selected tab as an "underline" accent.
    private lateinit var bottomNavIndicator: View

    // Shared ViewModel — same instance the Fragments pick up via `activityViewModels()`.
    // Used here to kick off the one-shot update check at startup.
    private val viewModel: TranslationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the saved theme BEFORE super.onCreate so the first frame is already correct
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        when (prefs.getString("theme", "system")) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Seed the Compose theme flag from the current uiMode so Compose and XML agree.
        ThemeState.isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        // Bind XML views and hand the ViewPager its fragment adapter.
        viewPager = findViewById(R.id.viewPager)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavIndicator = findViewById(R.id.bottomNavIndicator)

        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        // Bottom-nav -> ViewPager: tapping a tab switches the page AND dismisses any open overlay.
        bottomNavigation.setOnItemSelectedListener { item ->
            closeOverlayIfOpen()
            when (item.itemId) {
                R.id.navigation_home -> viewPager.currentItem = 0
                R.id.navigation_languages -> viewPager.currentItem = 1
                R.id.navigation_history -> viewPager.currentItem = 2
            }
            // Slide the underline strip to sit on top of the newly selected tab.
            positionBottomNavIndicator(item.itemId)
            true
        }

        // ViewPager -> Bottom-nav: keep the tab highlight correct when the user swipes pages.
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val itemId = when (position) {
                    0 -> R.id.navigation_home
                    1 -> R.id.navigation_languages
                    2 -> R.id.navigation_history
                    else -> R.id.navigation_home
                }
                bottomNavigation.selectedItemId = itemId
                // Keep the indicator in sync when the user swipes rather than taps.
                positionBottomNavIndicator(itemId)
            }
        })

        // Place the indicator under whichever tab starts selected (Home by default).
        // `post` waits for layout so the item-view widths are real, not zero.
        bottomNavigation.post {
            positionBottomNavIndicator(bottomNavigation.selectedItemId)
        }

        // Toolbar menu: only handle the gear icon to toggle the settings overlay.
        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                toggleSettings()
                true
            } else {
                false
            }
        }

        // Custom back handling: if the settings/PDF overlay is open, pressing BACK dismisses it
        // instead of leaving the activity.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val container = findViewById<FrameLayout>(R.id.settingsContainer)
                // KTX `isVisible` maps directly to VISIBLE/GONE — a clean replacement for the raw constants.
                if (container.isVisible) {
                    // Overlay was open — hide it and unwind the fragment back-stack.
                    container.isVisible = false
                    supportFragmentManager.popBackStack()
                } else {
                    // No overlay — fall through to the platform default (finish activity).
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        // Restore the settings overlay if it was showing before a configuration change.
        if (savedInstanceState?.getBoolean("settings_open") == true) {
            showSettings()
        }

        // Paint toolbar/nav/status-bar to match current theme and poll GitHub for a new app version.
        updateSystemBars()
        // GitHub-direct update check — HTTP GET decides if a newer version exists;
        // the Settings screen shows a non-blocking banner. Tapping Update streams the
        // APK from GitHub and hands it to the system package installer.
        viewModel.checkForAppUpdate(BuildConfig.VERSION_CODE)
    }

    /** React to system dark/light mode flips without a full activity recreate. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeState.isDark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        updateSystemBars()
    }

    /** Persist whether the settings overlay is visible so rotation preserves it. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val container = findViewById<FrameLayout>(R.id.settingsContainer)
        outState.putBoolean("settings_open", container.isVisible)
    }

    /** Open the translated-PDF viewer. Called by HomeFragment / HistoryFragment. */
    fun showPdfViewer() {
        val container = findViewById<FrameLayout>(R.id.settingsContainer)
        container.isVisible = true
        supportFragmentManager.beginTransaction()
            .replace(R.id.settingsContainer, PdfViewerFragment())
            .addToBackStack("pdfViewer") // Named entry so we can specifically unwind this overlay.
            .commit()
    }

    /** Close the translated-PDF viewer. Called by PdfViewerFragment's back button. */
    fun hidePdfViewer() {
        val container = findViewById<FrameLayout>(R.id.settingsContainer)
        container.isVisible = false
        supportFragmentManager.popBackStack()
    }

    /**
     * Repaint toolbar, bottom nav and system bars to match the current theme.
     * Called from onCreate and whenever the configuration changes at runtime.
     */
    private fun updateSystemBars() {
        val isDark = ThemeState.isDark
        // Pick the chrome background / foreground colors based on theme.
        val bgColor = if (isDark) getColor(R.color.dark_grey) else getColor(R.color.light_grey)
        val textColor = if (isDark) getColor(R.color.white) else getColor(R.color.black)

        // Tint the toolbar background, title and the (gear) menu icon.
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setBackgroundColor(bgColor)
        toolbar.setTitleTextColor(textColor)
        toolbar.menu.findItem(R.id.action_settings)?.icon?.setTint(textColor)

        // Tint the bottom nav background and icon/text color lists.
        bottomNavigation.setBackgroundColor(bgColor)
        bottomNavigation.itemIconTintList = ColorStateList.valueOf(textColor)
        bottomNavigation.itemTextColor = ColorStateList.valueOf(textColor)
        // Paint the selected-tab underline strip in the same foreground color as the icons/text,
        // so it's always visible against the bottom-bar background.
        if (::bottomNavIndicator.isInitialized) {
            bottomNavIndicator.setBackgroundColor(textColor)
        }

        // Status bar + window background. `statusBarColor` is deprecated on API 35 but still works.
        @Suppress("DEPRECATION")
        window.statusBarColor = bgColor
        // KTX `Int.toDrawable()` wraps a color-int in a ColorDrawable — cleaner than calling the constructor.
        val windowBg = if (isDark) getColor(R.color.black) else getColor(R.color.white)
        window.setBackgroundDrawable(windowBg.toDrawable())

        // Light-mode → dark status-bar icons and vice versa, so they're always visible.
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !isDark
    }

    /**
     * Move the underline strip so it sits on top of the selected bottom-nav tab.
     * Each menu item's view in a BottomNavigationView reuses its menu ID, so we can
     * look the tab up directly by `selectedItemId`. Width is matched to the tab so
     * the strip spans exactly that tab's horizontal extent.
     */
    private fun positionBottomNavIndicator(selectedItemId: Int) {
        val itemView = bottomNavigation.findViewById<View>(selectedItemId) ?: return
        // Wait one layout pass if the tab hasn't been measured yet (first frame).
        if (itemView.width == 0) {
            itemView.post { positionBottomNavIndicator(selectedItemId) }
            return
        }
        // Resize the strip to the tab's width, then slide it horizontally into place.
        val lp = bottomNavIndicator.layoutParams
        if (lp.width != itemView.width) {
            lp.width = itemView.width
            bottomNavIndicator.layoutParams = lp
        }
        bottomNavIndicator.animate()
            .translationX(itemView.x)
            .setDuration(180L)
            .start()
    }

    /** Safety helper: if the settings/PDF overlay is showing, take it down. */
    private fun closeOverlayIfOpen() {
        val container = findViewById<FrameLayout>(R.id.settingsContainer)
        if (container.isVisible) {
            container.isVisible = false
            supportFragmentManager.popBackStack()
        }
    }

    /** Toggle between showing and hiding the settings overlay (gear icon behavior). */
    private fun toggleSettings() {
        val container = findViewById<FrameLayout>(R.id.settingsContainer)
        if (container.isVisible) {
            container.isVisible = false
            supportFragmentManager.popBackStack()
        } else {
            showSettings()
        }
    }

    /** Put the Settings fragment into the overlay container and make it visible. */
    private fun showSettings() {
        val container = findViewById<FrameLayout>(R.id.settingsContainer)
        container.isVisible = true
        supportFragmentManager.beginTransaction()
            .replace(R.id.settingsContainer, SettingsFragment())
            .addToBackStack(null) // Anonymous entry — closed by the back button or gear toggle.
            .commit()
    }

    /**
     * Adapter that tells the ViewPager2 which fragment to build for each tab index.
     * FragmentStateAdapter handles lifecycle and fragment caching for us.
     * Not `inner`: the adapter only uses its constructor argument, so avoiding the
     * implicit outer-class reference sidesteps a potential leak and is idiomatic.
     */
    private class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        // We have exactly three primary tabs.
        override fun getItemCount(): Int = 3

        // Map tab index → Fragment instance. Fallback protects against unexpected indices.
        override fun createFragment(position: Int) = when (position) {
            0 -> HomeFragment()
            1 -> LanguagesFragment()
            2 -> HistoryFragment()
            else -> HomeFragment()
        }
    }
}

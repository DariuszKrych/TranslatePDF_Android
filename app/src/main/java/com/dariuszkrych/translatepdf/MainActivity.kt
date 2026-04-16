package com.dariuszkrych.translatepdf

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.dariuszkrych.translatepdf.ui.fragments.HistoryFragment
import com.dariuszkrych.translatepdf.ui.fragments.HomeFragment
import com.dariuszkrych.translatepdf.ui.fragments.LanguagesFragment
import com.dariuszkrych.translatepdf.ui.fragments.SettingsFragment
import com.dariuszkrych.translatepdf.ui.theme.ThemeState
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        when (prefs.getString("theme", "system")) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ThemeState.isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        viewPager = findViewById(R.id.viewPager)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        bottomNavigation.setOnItemSelectedListener { item ->
            closeSettingsIfOpen()
            when (item.itemId) {
                R.id.navigation_home -> viewPager.currentItem = 0
                R.id.navigation_languages -> viewPager.currentItem = 1
                R.id.navigation_history -> viewPager.currentItem = 2
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0 -> bottomNavigation.selectedItemId = R.id.navigation_home
                    1 -> bottomNavigation.selectedItemId = R.id.navigation_languages
                    2 -> bottomNavigation.selectedItemId = R.id.navigation_history
                }
            }
        })

        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                toggleSettings()
                true
            } else {
                false
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val container = findViewById<FrameLayout>(R.id.settingsContainer)
                if (container.visibility == View.VISIBLE) {
                    container.visibility = View.GONE
                    supportFragmentManager.popBackStack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        if (savedInstanceState?.getBoolean("settings_open") == true) {
            showSettings()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeState.isDark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        updateViewColors()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val container = findViewById<FrameLayout>(R.id.settingsContainer)
        outState.putBoolean("settings_open", container.visibility == View.VISIBLE)
    }

    private fun updateViewColors() {
        val isDark = ThemeState.isDark

        val bgColor = if (isDark) getColor(R.color.dark_grey) else getColor(R.color.light_grey)
        val textColor = if (isDark) getColor(R.color.white) else getColor(R.color.black)
        val windowBg = if (isDark) getColor(R.color.black) else getColor(R.color.white)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setBackgroundColor(bgColor)
        toolbar.setTitleTextColor(textColor)
        toolbar.menu.findItem(R.id.action_settings)?.icon?.setTint(textColor)

        bottomNavigation.setBackgroundColor(bgColor)
        bottomNavigation.itemIconTintList = ColorStateList.valueOf(textColor)
        bottomNavigation.itemTextColor = ColorStateList.valueOf(textColor)

        val settingsContainer = findViewById<FrameLayout>(R.id.settingsContainer)
        settingsContainer.setBackgroundColor(windowBg)

        @Suppress("DEPRECATION")
        window.statusBarColor = bgColor
        window.setBackgroundDrawable(ColorDrawable(windowBg))

        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !isDark
    }

    private fun closeSettingsIfOpen() {
        val container = findViewById<FrameLayout>(R.id.settingsContainer)
        if (container.visibility == View.VISIBLE) {
            container.visibility = View.GONE
            supportFragmentManager.popBackStack()
        }
    }

    private fun toggleSettings() {
        val container = findViewById<FrameLayout>(R.id.settingsContainer)
        if (container.visibility == View.VISIBLE) {
            container.visibility = View.GONE
            supportFragmentManager.popBackStack()
        } else {
            showSettings()
        }
    }

    private fun showSettings() {
        val container = findViewById<FrameLayout>(R.id.settingsContainer)
        container.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.settingsContainer, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int) = when (position) {
            0 -> HomeFragment()
            1 -> LanguagesFragment()
            2 -> HistoryFragment()
            else -> HomeFragment()
        }
    }
}

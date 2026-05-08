package com.dariuszkrych.translatepdf.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dariuszkrych.translatepdf.R
import com.dariuszkrych.translatepdf.LanguageInfo
import com.dariuszkrych.translatepdf.TranslationViewModel
import com.dariuszkrych.translatepdf.data.TranslationRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home tab UI. Stateless composable. Every value comes from the caller (the
 * Fragment binds it to the ViewModel). Lets the user pick a PDF, choose an
 * extraction method, see translation progress, and act on the translated result.
 */
@Composable
fun HomeScreen(
    fileName: String,
    extractionMethod: String,
    isTranslating: Boolean,
    translationProgress: String,
    translationPercent: Float,
    sourceLang: String,
    targetLang: String,
    hasPdf: Boolean,
    onPickPdf: () -> Unit,
    onExtractionMethodChanged: (String) -> Unit,
    onTranslate: () -> Unit,
    onViewTranslated: () -> Unit,
    onDownloadTranslated: () -> Unit,
    hasTranslatedPdf: Boolean
) {
    // Surface paints the theme's background behind everything, filling the whole tab.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // File picker card. Tapping anywhere on the card opens the Storage
            // Access Framework picker filtered to PDFs.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // Disable the click while a translation is running so the picker
                    // cannot swap the file mid operation.
                    .clickable(enabled = !isTranslating) { onPickPdf() },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Big PDF icon makes the target area obvious.
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Swap the label depending on whether the user has already picked a file.
                        if (fileName.isNotEmpty()) {
                            Text(fileName, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.home_tap_change))
                        } else {
                            Text(stringResource(R.string.home_open_pdf), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.home_tap_select))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Extraction method toggle. Choice between OCR and Direct.
            Text(
                stringResource(R.string.home_extraction_method),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pairs of (label shown to the user) and (internal key stored in the VM).
                val methods = listOf(
                    stringResource(R.string.home_method_ocr) to "ocr",
                    stringResource(R.string.home_method_direct) to "direct"
                )
                methods.forEach { (label, value) ->
                    // Highlight the selected option as a filled Button. The others are outlined.
                    if (extractionMethod == value) {
                        Button(onClick = {}) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { onExtractionMethodChanged(value) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                        ) { Text(label) }
                    }
                }
            }

            // Language pair display or prompt to pick one.
            if (sourceLang.isNotEmpty() && targetLang.isNotEmpty()) {
                // Both languages chosen. Show human readable names as "Source to Target".
                Text(
                    stringResource(
                        R.string.home_lang_pair_format,
                        TranslationViewModel.languageDisplayName(sourceLang),
                        TranslationViewModel.languageDisplayName(targetLang)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                // Nudge the user toward the Languages tab if either side is unset.
                Text(
                    stringResource(R.string.home_select_languages_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress bar during translation, or a final status message afterwards.
            if (isTranslating) {
                LinearProgressIndicator(
                    progress = { translationPercent / 100f }, // Indicator expects a 0 to 1 fraction.
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_progress_format, translationPercent.toInt(), translationProgress),
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else if (translationProgress.isNotEmpty() && !hasTranslatedPdf) {
                // No active translation but we still have a status message (e.g. an error).
                Text(translationProgress, color = MaterialTheme.colorScheme.onBackground)
            }

            // Translate button. Only shown when every prerequisite is met.
            if (hasPdf && !isTranslating && sourceLang.isNotEmpty() && targetLang.isNotEmpty()) {
                Button(
                    onClick = onTranslate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.home_translate))
                }
            }

            // Post-translation actions: view + download
            if (hasTranslatedPdf && !isTranslating) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onViewTranslated,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) {
                    Text(stringResource(R.string.home_view_translated))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDownloadTranslated,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.home_download_translated))
                }
            }
        }
    }
}

/**
 * Languages tab UI. Shows chips for picking source/target languages, followed by
 * two grouped lists: already-downloaded models, and available-to-download models.
 */
@Composable
fun LanguagesScreen(
    languages: List<LanguageInfo>,
    sourceLang: String,
    targetLang: String,
    downloadingLangs: Set<String>,
    onSourceSelected: (String) -> Unit,
    onTargetSelected: (String) -> Unit,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    // Local Compose state for which picker dialog (if any) is open.
    // Using the non delegated form (.value read and write) avoids the IDE's
    // "assigned value is never read" false positive it raises for by delegated
    // state inside lambdas.
    val showSourcePicker = remember { mutableStateOf(false) }
    val showTargetPicker = remember { mutableStateOf(false) }

    // Only downloaded languages can be chosen as source or target. The translator
    // needs both offline models present.
    val downloadedLangs = languages.filter { it.downloaded }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.lang_select_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            // Row of two chips with a visual translates to icon between them.
            // Tapping a chip opens the corresponding language picker dialog.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val unsetPlaceholder = stringResource(R.string.lang_unset_placeholder)
                AssistChip(
                    onClick = { showSourcePicker.value = true },
                    label = { Text(if (sourceLang.isNotEmpty()) sourceLang.uppercase() else unsetPlaceholder) }
                )
                // AutoMirrored TrendingFlat flips its direction for RTL locales,
                // so the visual reading order always matches the device language.
                Icon(Icons.AutoMirrored.Filled.TrendingFlat,
                    contentDescription = stringResource(R.string.lang_translates_to_cd),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
                AssistChip(
                    onClick = { showTargetPicker.value = true },
                    label = { Text(if (targetLang.isNotEmpty()) targetLang.uppercase() else unsetPlaceholder) }
                )
            }

            HorizontalDivider()

            // Single LazyColumn with two logical sections, Downloaded and Available.
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (downloadedLangs.isNotEmpty()) {
                    // Section header for the downloaded group.
                    item {
                        Text(
                            stringResource(R.string.lang_section_downloaded),
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    items(downloadedLangs, key = { "dl_${it.code}" }) { lang ->
                        ListItem(
                            headlineContent = { Text(lang.name) },
                            supportingContent = {
                                // English is the ML Kit NMT pivot. It is always present
                                // and shares bytes with every other language pair, so
                                // there is no meaningful standalone size to show.
                                if (lang.code == "en") {
                                    Text(stringResource(R.string.lang_core_preinstalled))
                                } else if (lang.sizeMb > 0f) {
                                    val sizeStr = if (lang.sizeMb >= 1f) {
                                        stringResource(R.string.lang_size_mb_1f, lang.sizeMb)
                                    } else {
                                        stringResource(R.string.lang_size_mb_2f, lang.sizeMb)
                                    }
                                    Text(stringResource(R.string.lang_installed_with_size_format, sizeStr))
                                } else {
                                    Text(stringResource(R.string.lang_installed))
                                }
                            },
                            trailingContent = {
                                // English is the base model and cannot be deleted, so
                                // we show a checkmark in place of the delete icon.
                                if (lang.code != "en") {
                                    IconButton(onClick = { onDelete(lang.code) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.lang_delete_cd))
                                    }
                                } else {
                                    // Match IconButton's 40dp default so the check lines
                                    // up visually with the Delete icon on neighboring
                                    // rows. The extra 3dp tweaks alignment that was
                                    // still slightly off in practice.
                                    Box(
                                        modifier = Modifier.size(45.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            }
                        )
                    }
                }

                // Everything that isn't downloaded yet goes in the "Available" section.
                val availableLangs = languages.filter { !it.downloaded }
                if (availableLangs.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.lang_section_available),
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    items(availableLangs, key = { "av_${it.code}" }) { lang ->
                        ListItem(
                            headlineContent = { Text(lang.name) },
                            // Extra line with "Downloading..." only while a download is in flight.
                            supportingContent = if (lang.code in downloadingLangs) {
                                { Text(stringResource(R.string.lang_downloading)) }
                            } else null,
                            trailingContent = {
                                // Spinner while in-flight, otherwise a download button.
                                if (lang.code in downloadingLangs) {
                                    Box(
                                        modifier = Modifier.size(45.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                } else {
                                    IconButton(onClick = { onDownload(lang.code) }) {
                                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.lang_download_cd))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal dialogs for picking source and target. Shown based on the local
    // state flags toggled above.
    if (showSourcePicker.value) {
        LanguagePickerDialog(
            title = stringResource(R.string.lang_picker_source_title),
            languages = downloadedLangs,
            onSelected = { onSourceSelected(it); showSourcePicker.value = false },
            onDismiss = { showSourcePicker.value = false }
        )
    }
    if (showTargetPicker.value) {
        LanguagePickerDialog(
            title = stringResource(R.string.lang_picker_target_title),
            languages = downloadedLangs,
            onSelected = { onTargetSelected(it); showTargetPicker.value = false },
            onDismiss = { showTargetPicker.value = false }
        )
    }
}

/**
 * Modal picker for choosing a source or target language from the downloaded set.
 * An empty list prompts the user to download a pack first.
 */
@Composable
private fun LanguagePickerDialog(
    title: String,
    languages: List<LanguageInfo>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                // One full-width TextButton per language; tapping it picks that code.
                items(languages) { lang ->
                    TextButton(
                        onClick = { onSelected(lang.code) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.lang_picker_item_format, lang.name, lang.code.uppercase()),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // Empty-state hint if no models are downloaded at all yet.
                if (languages.isEmpty()) {
                    item {
                        Text(stringResource(R.string.lang_picker_empty))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lang_picker_cancel)) }
        }
    )
}

/**
 * History tab UI. Search field on top, then a LazyColumn of prior translations
 * (tap to reopen, trash icon to delete).
 */
@Composable
fun HistoryScreen(
    records: List<TranslationRecord>,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onRecordClick: (TranslationRecord) -> Unit,
    onRecordDelete: (TranslationRecord) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            // Search box. Every keystroke triggers a fresh DB lookup via the VM's loadHistory().
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                label = { Text(stringResource(R.string.history_search_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.history_recent),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (records.isEmpty()) {
                // Empty state message. Different wording for no history at all vs. no
                // matches for the current search query.
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    if (searchQuery.isBlank()) stringResource(R.string.history_empty) else stringResource(R.string.history_no_results),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Render one ListItem per record. key = { it.id } keeps scroll
                // position stable across list mutations such as deleting an item.
                LazyColumn {
                    items(records, key = { it.id }) { record ->
                        // Format timestamp for display. Use device locale for month/weekday names.
                        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            .format(Date(record.timestamp))
                        val srcName = TranslationViewModel.languageDisplayName(record.sourceLang)
                        val tgtName = TranslationViewModel.languageDisplayName(record.targetLang)

                        ListItem(
                            headlineContent = { Text(record.fileName) },
                            supportingContent = { Text(stringResource(R.string.history_supporting_format, srcName, tgtName, dateStr)) },
                            trailingContent = {
                                IconButton(onClick = { onRecordDelete(record) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            },
                            // Tapping anywhere on the row re-opens the PDF in the viewer.
                            modifier = Modifier.clickable { onRecordClick(record) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single line theme button label. Calls onShrink when the laid out text would
 * visually overflow its container, so the surrounding screen can drop the
 * font size in lockstep across all three theme buttons.
 */
@Composable
private fun ThemeButtonLabel(
    label: String,
    fontSize: TextUnit,
    onShrink: () -> Unit
) {
    Text(
        text = label,
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result ->
            if (result.hasVisualOverflow) onShrink()
        }
    )
}

/**
 * Settings overlay UI. Three theme toggle buttons, an optional update banner
 * and a link to the project's GitHub repo. Defaults on every parameter exist
 * so Compose previews do not break when no callbacks are wired up.
 */

@Composable
fun SettingsScreen(
    currentTheme: String = "system",
    onThemeSelected: (String) -> Unit = {},
    onReviewClick: () -> Unit = {},
    updateAvailable: Boolean = false,
    latestVersionName: String? = null,
    updateDownloading: Boolean = false,
    updateProgress: Int = 0,
    updateError: String? = null,
    onUpdateClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.settings_select_theme),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(modifier = Modifier.padding(vertical = 16.dp)) {
                // Pair of (user facing label) and (persisted key).
                val themes = listOf(
                    stringResource(R.string.theme_system) to "system",
                    stringResource(R.string.theme_dark) to "dark",
                    stringResource(R.string.theme_light) to "light"
                )
                // Shrinks all three labels in lockstep if any would wrap to a
                // second line. Long translations (for example Russian "Системная",
                // Maltese "Tas-sistema", Polish "Systemowy") can otherwise
                // overflow the right most button.
                var themeFontSize by remember(themes) { mutableStateOf(14.sp) }
                themes.forEachIndexed { index, (label, value) ->
                    // Small horizontal gap between buttons. Skip before the first.
                    if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                    val isSelected = currentTheme == value
                    val onShrink = { if (themeFontSize > 12.sp) themeFontSize = 12.sp }
                    // Selected option is filled. The others are outlined for contrast.
                    if (isSelected) {
                        Button(onClick = { onThemeSelected(value) }) {
                            ThemeButtonLabel(label, themeFontSize, onShrink)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onThemeSelected(value) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onBackground
                            )
                        ) { ThemeButtonLabel(label, themeFontSize, onShrink) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // "Update available" banner. Only rendered when the HTTP check
            // against GitHub's version.json reports a higher versionCode than
            // BuildConfig.VERSION_CODE. Tapping Update streams the new APK from
            // GitHub and hands it to the system package installer.
            if (updateAvailable) {
                UpdateAvailableBanner(
                    latestVersionName = latestVersionName,
                    downloading = updateDownloading,
                    progress = updateProgress,
                    error = updateError,
                    onUpdateClick = onUpdateClick
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Card that opens the Play Store review page when tapped.
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onReviewClick() }
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_send_review)) },
                    supportingContent = { Text(stringResource(R.string.settings_review_subtitle)) }
                )
            }

            // weight(1f) pushes the footer text to the bottom of the column.
            Spacer(modifier = Modifier.weight(1f))

            Text(
                stringResource(R.string.settings_thank_you),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * Non blocking "Update available" banner shown in Settings when the version
 * check finds a newer release on GitHub. Tapping Update kicks off the in app
 * APK download and hands the result to the system package installer.
 */
@Composable
private fun UpdateAvailableBanner(
    latestVersionName: String?,
    downloading: Boolean,
    progress: Int,
    error: String?,
    onUpdateClick: () -> Unit
) {
    val subtitle = when {
        downloading -> stringResource(R.string.update_downloading, progress)
        error != null -> stringResource(R.string.update_failed, error)
        latestVersionName != null ->
            stringResource(R.string.update_available_subtitle_with_version, latestVersionName)
        else -> stringResource(R.string.update_available_subtitle)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null
                )
            },
            headlineContent = { Text(stringResource(R.string.update_available_title)) },
            supportingContent = { Text(subtitle) },
            trailingContent = {
                TextButton(onClick = onUpdateClick, enabled = !downloading) {
                    Text(stringResource(R.string.update_action))
                }
            }
        )
    }
}

/**
 * Overlay UI for viewing the translated PDF page by page.
 * Header row contains a Back button on the left and a Share icon on the right.
 * The body is a vertically scrolling list of page bitmaps.
 */
@Composable
fun PdfViewerScreen(
    pages: List<Bitmap>,
    onShare: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed header. Back button on the left, share icon on the right.
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.pdf_back)) }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.pdf_share_cd))
                }
            }

            if (pages.isEmpty()) {
                // Still rendering bitmaps. Show a centered spinner until they arrive.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Scrollable list of page bitmaps, each wrapped in an elevated Card.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pages.size) { index ->
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                            // The Bitmap to ImageBitmap conversion is a cheap wrapper, no copy.
                            Image(
                                bitmap = pages[index].asImageBitmap(),
                                contentDescription = stringResource(R.string.pdf_page_cd_format, index + 1),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

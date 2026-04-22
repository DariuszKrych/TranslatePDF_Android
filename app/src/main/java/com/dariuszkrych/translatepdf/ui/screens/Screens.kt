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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.dariuszkrych.translatepdf.LanguageInfo
import com.dariuszkrych.translatepdf.TranslationViewModel
import com.dariuszkrych.translatepdf.data.TranslationRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home tab UI. Stateless composable — every value comes from the caller (the Fragment
 * binds it to the ViewModel). Lets the user pick a PDF, choose extraction method,
 * see translation progress, and act on the translated result.
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

            // File picker card: tapping anywhere on the card opens SAF (Storage Access Framework)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // Disable the click while a translation is running so the picker
                    // can't swap the file mid-operation.
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
                            Text("Tap to change file")
                        } else {
                            Text("Open a PDF", style = MaterialTheme.typography.headlineSmall)
                            Text("Tap to select file to translate.")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Extraction-method toggle: OCR vs Direct
            Text(
                "Select PDF text extraction method",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pairs of (label shown to the user) → (internal key stored in VM).
                val methods = listOf("OCR" to "ocr", "Direct" to "direct")
                methods.forEach { (label, value) ->
                    // Highlight the selected option as a filled Button; others are outlined.
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

            // Language pair display / prompt
            if (sourceLang.isNotEmpty() && targetLang.isNotEmpty()) {
                // Both languages chosen show human-readable names as "Source -> Target".
                Text(
                    "${TranslationViewModel.languageDisplayName(sourceLang)} -> ${TranslationViewModel.languageDisplayName(targetLang)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                // Nudge the user toward the Languages tab if either side is unset.
                Text(
                    "Select languages in the Languages tab",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress bar (during translation) or final message
            if (isTranslating) {
                LinearProgressIndicator(
                    progress = { translationPercent / 100f }, // Indicator expects 0..1.
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${translationPercent.toInt()}% - $translationProgress",
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else if (translationProgress.isNotEmpty() && !hasTranslatedPdf) {
                // No active translation but we still have a status message (e.g. an error).
                Text(translationProgress, color = MaterialTheme.colorScheme.onBackground)
            }

            // "Translate" button — only shown when every prerequisite is met
            if (hasPdf && !isTranslating && sourceLang.isNotEmpty() && targetLang.isNotEmpty()) {
                Button(
                    onClick = onTranslate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Translate")
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
                    Text("View Translated PDF")
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
                    Text("Download Translated PDF")
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
    // Using the non-delegated form (`.value` read/write) avoids the IDE's "assigned value
    // is never read" false positive it raises for `by`-delegated state in lambdas.
    val showSourcePicker = remember { mutableStateOf(false) }
    val showTargetPicker = remember { mutableStateOf(false) }

    // Only downloaded languages can be chosen as source/target (translator needs both models offline).
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
                "Select Translation Languages",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            // Row of two chips with a visual arrow between them: SRC ----> TGT.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { showSourcePicker.value = true },
                    // Show "???" when nothing is picked yet.
                    label = { Text(if (sourceLang.isNotEmpty()) sourceLang.uppercase() else "???") }
                )
//                Text(
//                    " ----> ",
//                    modifier = Modifier.padding(horizontal = 16.dp),
//                    color = MaterialTheme.colorScheme.onBackground
//                )
                Icon(Icons.AutoMirrored.Filled.TrendingFlat,
                    contentDescription = "Translates to",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
                AssistChip(
                    onClick = { showTargetPicker.value = true },
                    // Show "???" when nothing is picked yet.
                    label = { Text(if (targetLang.isNotEmpty()) targetLang.uppercase() else "???") }
                )
            }

            HorizontalDivider()

            // Single LazyColumn with two logical sections — Downloaded / Available.
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (downloadedLangs.isNotEmpty()) {
                    // Section header for the downloaded group.
                    item {
                        Text(
                            "Downloaded",
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    items(downloadedLangs, key = { "dl_${it.code}" }) { lang ->
                        ListItem(
                            headlineContent = { Text(lang.name) },
                            supportingContent = {
                                // English is the ML Kit NMT pivot — it's always present
                                // and shares bytes with every other language pair, so
                                // there isn't a meaningful standalone size to show.
                                if (lang.code == "en") {
                                    Text("Core Pre-Installed")
                                } else if (lang.sizeMb > 0f) {
                                    val sizeStr = if (lang.sizeMb >= 1f) {
                                        "%.1f MB".format(lang.sizeMb)
                                    } else {
                                        "%.2f MB".format(lang.sizeMb)
                                    }
                                    Text("$sizeStr - Installed")
                                } else {
                                    Text("Installed")
                                }
                            },
                            trailingContent = {
                                // English is the base model — can't be deleted, show a checkmark instead.
                                if (lang.code != "en") {
                                    IconButton(onClick = { onDelete(lang.code) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                } else {
                                    // Match IconButton's 40dp default so the check lines up
                                    // visually with the Delete icon on neighboring rows.
                                    // Still was a bit off visually so added another 3dp.
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
                            "Available",
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    items(availableLangs, key = { "av_${it.code}" }) { lang ->
                        ListItem(
                            headlineContent = { Text(lang.name) },
                            // Extra line with "Downloading..." only while a download is active.
                            supportingContent = if (lang.code in downloadingLangs) {
                                { Text("Downloading...") }
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
                                        Icon(Icons.Default.Download, contentDescription = "Download")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal dialogs for picking source / target — shown based on local state flags above.
    if (showSourcePicker.value) {
        LanguagePickerDialog(
            title = "Source Language",
            languages = downloadedLangs,
            onSelected = { onSourceSelected(it); showSourcePicker.value = false },
            onDismiss = { showSourcePicker.value = false }
        )
    }
    if (showTargetPicker.value) {
        LanguagePickerDialog(
            title = "Target Language",
            languages = downloadedLangs,
            onSelected = { onTargetSelected(it); showTargetPicker.value = false },
            onDismiss = { showTargetPicker.value = false }
        )
    }
}

/**
 * Modal picker for choosing a source/target language from the downloaded set.
 * Empty list prompts the user to download a pack first.
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
                            "${lang.name} (${lang.code.uppercase()})",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // Empty-state hint if no models are downloaded at all yet.
                if (languages.isEmpty()) {
                    item {
                        Text("Download language packs first.")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
            // Search box — every keystroke debounces via the VM's `loadHistory()`.
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                label = { Text("Type to search history") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Recent Translations",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (records.isEmpty()) {
                // Empty-state message — different wording for no-history vs. no-search-results.
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    if (searchQuery.isBlank()) "No translations yet." else "No results found.",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Render one ListItem per record. `key = { it.id }` keeps scroll position
                // stable across list mutations (e.g., deleting an item).
                LazyColumn {
                    items(records, key = { it.id }) { record ->
                        // Format timestamp for display. Use device locale for month/weekday names.
                        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            .format(Date(record.timestamp))
                        val srcName = TranslationViewModel.languageDisplayName(record.sourceLang)
                        val tgtName = TranslationViewModel.languageDisplayName(record.targetLang)

                        ListItem(
                            headlineContent = { Text(record.fileName) },
                            supportingContent = { Text("$srcName -> $tgtName\n$dateStr") },
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
 * Settings overlay UI. Three theme toggle buttons plus a link to the Play Store
 * review page. Defaults on the parameters exist so Compose previews don't break.
 */
@Composable
fun SettingsScreen(
    currentTheme: String = "system",
    onThemeSelected: (String) -> Unit = {},
    onReviewClick: () -> Unit = {},
    updateAvailable: Boolean = false,
    latestVersionName: String? = null,
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
                "Select Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(modifier = Modifier.padding(vertical = 16.dp)) {
                // Pair of (user-facing label) → (persisted key).
                val themes = listOf("System" to "system", "Dark" to "dark", "Light" to "light")
                themes.forEachIndexed { index, (label, value) ->
                    // Small horizontal gap between buttons (skip before the first).
                    if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                    val isSelected = currentTheme == value
                    // Selected option is filled; the others are outlined for contrast.
                    if (isSelected) {
                        Button(onClick = { onThemeSelected(value) }) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { onThemeSelected(value) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onBackground
                            )
                        ) { Text(label) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // "Update available" banner — Flexible Update UX. Only rendered when the
            // HTTP check against GitHub's version.json reports a higher versionCode
            // than BuildConfig.VERSION_CODE. Tapping Update launches the Play Store.
            if (updateAvailable) {
                UpdateAvailableBanner(
                    latestVersionName = latestVersionName,
                    onUpdateClick = onUpdateClick
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Card that opens the Play Store review page when tapped.
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onReviewClick() }
            ) {
                ListItem(
                    headlineContent = { Text("Send a Review") },
                    supportingContent = { Text("Click here to review") }
                )
            }

            // `weight(1f)` pushes the footer text to the bottom of the column.
            Spacer(modifier = Modifier.weight(1f))

            Text(
                "Thank you for using TranslatePDF. ^^",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * Non-blocking "Update available" banner shown in Settings when the hybrid update
 * check finds a newer release on Google Play. Matches the visual style of the
 * adjacent "Send a Review" card so it feels native to the screen.
 */
@Composable
private fun UpdateAvailableBanner(
    latestVersionName: String?,
    onUpdateClick: () -> Unit
) {
    val subtitle = if (latestVersionName != null) {
        "Version $latestVersionName is now available on Google Play"
    } else {
        "A new version is now available on Google Play"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null
                )
            },
            headlineContent = { Text("Update available") },
            supportingContent = { Text(subtitle) },
            trailingContent = {
                TextButton(onClick = onUpdateClick) { Text("Update") }
            }
        )
    }
}

/**
 * Overlay UI for viewing the translated PDF page by page.
 * Header row: Back | Title | Share. Body: vertically scrolling bitmaps.
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
            // Fixed header: back button left, title center, share icon right.
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
            }

            if (pages.isEmpty()) {
                // Still rendering bitmaps — show a centered spinner until they arrive.
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
                            // Bitmap → ImageBitmap conversion is a cheap wrapper (no copy).
                            Image(
                                bitmap = pages[index].asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

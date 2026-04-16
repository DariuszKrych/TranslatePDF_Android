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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isTranslating) { onPickPdf() },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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

            Text(
                "Select PDF text extraction method",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val methods = listOf("OCR" to "ocr", "Direct" to "direct")
                methods.forEach { (label, value) ->
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

            if (sourceLang.isNotEmpty() && targetLang.isNotEmpty()) {
                Text(
                    "${TranslationViewModel.languageDisplayName(sourceLang)} -> ${TranslationViewModel.languageDisplayName(targetLang)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                Text(
                    "Select languages in the Languages tab",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isTranslating) {
                LinearProgressIndicator(
                    progress = { translationPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${translationPercent.toInt()}% - $translationProgress",
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else if (translationProgress.isNotEmpty() && !hasTranslatedPdf) {
                Text(translationProgress, color = MaterialTheme.colorScheme.onBackground)
            }

            if (hasPdf && !isTranslating && sourceLang.isNotEmpty() && targetLang.isNotEmpty()) {
                Button(
                    onClick = onTranslate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Translate")
                }
            }

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
    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { showSourcePicker = true },
                    label = { Text(if (sourceLang.isNotEmpty()) sourceLang.uppercase() else "???") }
                )
                Text(
                    " ----> ",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                AssistChip(
                    onClick = { showTargetPicker = true },
                    label = { Text(if (targetLang.isNotEmpty()) targetLang.uppercase() else "???") }
                )
            }

            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (downloadedLangs.isNotEmpty()) {
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
                                if (lang.sizeMb > 0f) {
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
                                if (lang.code != "en") {
                                    IconButton(onClick = { onDelete(lang.code) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }

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
                            supportingContent = if (lang.code in downloadingLangs) {
                                { Text("Downloading...") }
                            } else null,
                            trailingContent = {
                                if (lang.code in downloadingLangs) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
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

    if (showSourcePicker) {
        LanguagePickerDialog(
            title = "Source Language",
            languages = downloadedLangs,
            onSelected = { onSourceSelected(it); showSourcePicker = false },
            onDismiss = { showSourcePicker = false }
        )
    }
    if (showTargetPicker) {
        LanguagePickerDialog(
            title = "Target Language",
            languages = downloadedLangs,
            onSelected = { onTargetSelected(it); showTargetPicker = false },
            onDismiss = { showTargetPicker = false }
        )
    }
}

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
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    if (searchQuery.isBlank()) "No translations yet." else "No results found.",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    items(records, key = { it.id }) { record ->
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
                            modifier = Modifier.clickable { onRecordClick(record) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    currentTheme: String = "system",
    onThemeSelected: (String) -> Unit = {},
    onReviewClick: () -> Unit = {}
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
                val themes = listOf("System" to "system", "Dark" to "dark", "Light" to "light")
                themes.forEachIndexed { index, (label, value) ->
                    if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                    val isSelected = currentTheme == value
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

            Card(
                modifier = Modifier.fillMaxWidth().clickable { onReviewClick() }
            ) {
                ListItem(
                    headlineContent = { Text("Send a Review") },
                    supportingContent = { Text("Click here to review") }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "Thank you for using TranslatePDF. ^^",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

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
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text("Translated PDF", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
            }

            if (pages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pages.size) { index ->
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
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

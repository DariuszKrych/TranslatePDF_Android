package com.dariuszkrych.translatepdf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Open a PDF", style = MaterialTheme.typography.headlineSmall)
                        Text("Tap to select file to translate.")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Select PDF text extraction method",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {},
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) { Text("OCR") }
                OutlinedButton(
                    onClick = {},
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                ) { Text("Direct") }
            }
        }
    }
}

@Composable
fun LanguagesScreen() {
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
                AssistChip(onClick = {}, label = { Text("EN") })
                Text(
                    " ----> ",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                AssistChip(onClick = {}, label = { Text("MT") })
            }

            HorizontalDivider()

            Text(
                "Downloaded",
                modifier = Modifier.align(Alignment.Start).padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
            ListItem(
                headlineContent = { Text("English") },
                supportingContent = { Text("28 MB - Installed") },
                trailingContent = { Icon(Icons.Default.Check, contentDescription = null) }
            )

            Text(
                "Available",
                modifier = Modifier.align(Alignment.Start).padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
            ListItem(
                headlineContent = { Text("Maltese") },
                supportingContent = { Text("29 MB") },
                trailingContent = { IconButton(onClick = {}) { Icon(Icons.Default.Download, contentDescription = null) } }
            )
        }
    }
}

@Composable
fun HistoryScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Type to search history") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Recent Translations",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            ListItem(
                headlineContent = { Text("document_es.pdf") },
                supportingContent = { Text("Spanish -> Polish\n17 Apr 2027") }
            )
            ListItem(
                headlineContent = { Text("report_de.pdf") },
                supportingContent = { Text("German -> English\n14 Apr 2027") }
            )
        }
    }
}

@Composable
fun SettingsScreen(
    currentTheme: String = "system",
    onThemeSelected: (String) -> Unit = {}
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

            Card(modifier = Modifier.fillMaxWidth()) {
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

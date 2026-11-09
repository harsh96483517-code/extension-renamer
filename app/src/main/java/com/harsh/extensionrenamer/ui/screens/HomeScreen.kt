package com.harsh.extensionrenamer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.extensionrenamer.data.PreviewRow
import com.harsh.extensionrenamer.data.SelectedFile
import com.harsh.extensionrenamer.ui.components.ReportDialog
import com.harsh.extensionrenamer.ui.theme.BlueAccent
import com.harsh.extensionrenamer.ui.theme.PurplePrimary
import com.harsh.extensionrenamer.util.FileNameUtils
import com.harsh.extensionrenamer.viewmodel.UiState

private val QuickExtensions = listOf("jpg", "png", "webp", "pdf", "txt", "mp4", "zip", "docx")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    preview: List<PreviewRow>,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onRemoveFile: (String) -> Unit,
    onExtensionChange: (String) -> Unit,
    onAutoSuffixChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDismissReport: () -> Unit,
    onMessageShown: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val previewByUri = remember(preview) { preview.associateBy { it.file.key } }
    val snackbarHostState = remember { SnackbarHostState() }
    val allSelected = state.files.isNotEmpty() && state.selected.size == state.files.size
    val readyCount = preview.count { it.changed && !it.conflict }
    val blockedCount = preview.count { it.conflict }
    val unverified = preview.any { !it.verified }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Extension Renamer",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Rounded.History, contentDescription = "Rename history")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            if (state.files.isNotEmpty()) {
                BottomActionBar(
                    readyCount = readyCount,
                    state = state,
                    onRename = onRename
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { HeroCard(state.files.size, onPickFiles, onPickFolder) }

            item {
                ExtensionCard(
                    extension = state.targetExtension,
                    autoSuffix = state.autoSuffix,
                    onExtensionChange = onExtensionChange,
                    onAutoSuffixChange = onAutoSuffixChange
                )
            }

            if (state.files.isNotEmpty()) {
                item {
                    SelectionHeader(
                        selectedCount = state.selected.size,
                        totalCount = state.files.size,
                        allSelected = allSelected,
                        onSelectAll = onSelectAll
                    )
                }
                items(state.files, key = { it.key }) { file ->
                    FileRow(
                        file = file,
                        checked = file.key in state.selected,
                        preview = previewByUri[file.key],
                        onToggle = { onToggle(file.key) },
                        onRemove = { onRemoveFile(file.key) }
                    )
                }
                if (blockedCount > 0) {
                    item {
                        InfoLine(
                            "⛔ $blockedCount file(s) skipped: the target name already exists. " +
                                "Turn on auto-numbering to rename them as (1), (2)…",
                            MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (unverified) {
                    item {
                        InfoLine(
                            "Clash detection is unavailable for some files. Use “Select folder” " +
                                "for guaranteed no-overwrite renaming.",
                            MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item { EmptyState(onPickFiles) }
            }
        }
    }

    state.report?.let { ReportDialog(it, onDismissReport) }
}

@Composable
private fun HeroCard(fileCount: Int, onPickFiles: () -> Unit, onPickFolder: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(PurplePrimary, BlueAccent)))
                .padding(22.dp)
        ) {
            Column {
                Text(
                    "Bulk extension renamer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pick files, choose the new extension, preview every change, then rename in one tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f)
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPickFiles,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = PurplePrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Select files", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onPickFolder,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            Icons.Rounded.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Select folder")
                    }
                }
                AnimatedVisibility(visible = fileCount > 0) {
                    Text(
                        "$fileCount file(s) in the list",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionCard(
    extension: String,
    autoSuffix: Boolean,
    onExtensionChange: (String) -> Unit,
    onAutoSuffixChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "New extension",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Only the extension changes — file names stay exactly as they are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = extension,
                onValueChange = onExtensionChange,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("png") },
                leadingIcon = {
                    Text(".", style = MaterialTheme.typography.titleLarge)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.None
                )
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickExtensions.forEach { ext ->
                    FilterChip(
                        selected = extension == ext,
                        onClick = { onExtensionChange(ext) },
                        label = { Text(".$ext") },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = autoSuffix, onCheckedChange = onAutoSuffixChange)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Auto-number name clashes", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Uses “name (1).ext” instead of failing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    onSelectAll: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = allSelected, onCheckedChange = { onSelectAll(it) })
        Text(
            "$selectedCount of $totalCount selected",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onSelectAll(!allSelected) }) {
            Text(if (allSelected) "Clear" else "Select all")
        }
    }
}

@Composable
private fun FileRow(
    file: SelectedFile,
    checked: Boolean,
    preview: PreviewRow?,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (checked) 2.dp else 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (preview != null && preview.changed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (preview.conflict) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            preview.newName,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (preview.conflict) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        FileNameUtils.formatSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove from list",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoLine(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun EmptyState(onPickFiles: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No files yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Select as many files as you like — the app is fully offline and needs no permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onPickFiles) { Text("Select files") }
        }
    }
}

@Composable
private fun BottomActionBar(readyCount: Int, state: UiState, onRename: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (state.progressTotal > 0) {
            state.progressDone.toFloat() / state.progressTotal.toFloat()
        } else {
            0f
        },
        label = "renameProgress"
    )

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (state.isWorking) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.progressTotal > 0) {
                        "Renaming ${state.progressDone} of ${state.progressTotal}…"
                    } else {
                        "Reading files…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    when (readyCount) {
                        0 -> "Nothing to rename yet — pick a different extension"
                        1 -> "1 file will be renamed"
                        else -> "$readyCount files will be renamed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRename,
                enabled = !state.isWorking && readyCount > 0,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rename all", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

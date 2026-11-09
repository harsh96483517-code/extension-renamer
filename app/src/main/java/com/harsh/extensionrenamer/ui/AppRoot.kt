package com.harsh.extensionrenamer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.extensionrenamer.data.RenamePlanner
import com.harsh.extensionrenamer.ui.screens.HistoryScreen
import com.harsh.extensionrenamer.ui.screens.HomeScreen
import com.harsh.extensionrenamer.viewmodel.RenamerViewModel

private enum class Screen { Home, History }

@Composable
fun AppRoot(viewModel: RenamerViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.Home) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addUris(uris) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) viewModel.addTree(uri) }

    val preview = remember(
        state.files, state.selected, state.targetExtension, state.autoSuffix, state.existingNames
    ) {
        RenamePlanner.build(
            files = state.files,
            selected = state.selected,
            extension = state.targetExtension,
            existingNames = state.existingNames,
            autoSuffix = state.autoSuffix
        )
    }

    when (screen) {
        Screen.Home -> HomeScreen(
            state = state,
            preview = preview,
            onPickFiles = { filePicker.launch(arrayOf("*/*")) },
            onPickFolder = { folderPicker.launch(null) },
            onToggle = viewModel::toggleSelection,
            onSelectAll = viewModel::selectAll,
            onRemoveFile = viewModel::removeFile,
            onExtensionChange = viewModel::setExtension,
            onAutoSuffixChange = viewModel::setAutoSuffix,
            onRename = viewModel::renameSelected,
            onDismissReport = viewModel::dismissReport,
            onMessageShown = viewModel::consumeMessage,
            onOpenHistory = {
                viewModel.refreshHistory()
                screen = Screen.History
            }
        )

        Screen.History -> HistoryScreen(
            operations = state.history,
            isWorking = state.isWorking,
            onBack = { screen = Screen.Home },
            onUndo = viewModel::undoOperation,
            onClear = viewModel::clearHistory
        )
    }
}

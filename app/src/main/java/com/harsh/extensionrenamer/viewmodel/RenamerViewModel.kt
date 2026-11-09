package com.harsh.extensionrenamer.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.extensionrenamer.data.FileRenamer
import com.harsh.extensionrenamer.data.HistoryItem
import com.harsh.extensionrenamer.data.HistoryOperation
import com.harsh.extensionrenamer.data.HistoryStore
import com.harsh.extensionrenamer.data.RenamePlanner
import com.harsh.extensionrenamer.data.SelectedFile
import com.harsh.extensionrenamer.util.FileNameUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class UiState(
    val files: List<SelectedFile> = emptyList(),
    val selected: Set<String> = emptySet(),
    val targetExtension: String = "png",
    val autoSuffix: Boolean = true,
    val existingNames: Map<String, Set<String>> = emptyMap(),
    val isWorking: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val report: HistoryOperation? = null,
    val history: List<HistoryOperation> = emptyList(),
    val message: String? = null
)

class RenamerViewModel(application: Application) : AndroidViewModel(application) {

    private val store = HistoryStore(application)
    private val _uiState = MutableStateFlow(UiState(history = store.load()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ---------------------------------------------------------------- selection

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val known = _uiState.value.files.map { it.key }.toSet()
            val fresh = uris.filter { it.toString() !in known }
            if (fresh.isEmpty()) return@launch

            _uiState.update { it.copy(isWorking = true, progressDone = 0, progressTotal = 0) }

            withContext(Dispatchers.IO) {
                fresh.forEach { uri ->
                    try {
                        app.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: Throwable) {
                        // Not every provider offers persistable grants; the session grant still works.
                    }
                }
            }

            val metas = withContext(Dispatchers.IO) {
                fresh.mapNotNull { FileRenamer.queryMeta(app, it) }
            }
            val names = withContext(Dispatchers.IO) { readSiblingNames(metas) }

            _uiState.update { state ->
                val added = metas.filter { it.key !in state.files.map(SelectedFile::key).toSet() }
                state.copy(
                    files = state.files + added,
                    selected = state.selected + added.map { it.key },
                    existingNames = state.existingNames + names,
                    isWorking = false
                )
            }
        }
    }

    /** Enumerates a whole folder picked with ACTION_OPEN_DOCUMENT_TREE. */
    fun addTree(treeUri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            _uiState.update { it.copy(isWorking = true, progressDone = 0, progressTotal = 0) }

            try {
                app.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) {
                // Session grant is enough for this run.
            }

            val listing = withContext(Dispatchers.IO) {
                val files = ArrayList<SelectedFile>()
                val names = HashSet<String>()
                try {
                    val parentId = DocumentsContract.getTreeDocumentId(treeUri)
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    )
                    app.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(0) ?: continue
                            val name = cursor.getString(1) ?: continue
                            val size = if (cursor.isNull(2)) 0L else cursor.getLong(2)
                            val mime = cursor.getString(3)
                            names.add(name)
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                            files.add(
                                SelectedFile(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                                    name = name,
                                    size = size,
                                    mimeType = mime,
                                    parentKey = parentId
                                )
                            )
                        }
                    }
                    parentId to (files to names)
                } catch (t: Throwable) {
                    null
                }
            }

            if (listing == null) {
                _uiState.update {
                    it.copy(isWorking = false, message = "Could not read that folder")
                }
                return@launch
            }

            val (parentId, pair) = listing
            val (files, names) = pair
            _uiState.update { state ->
                val known = state.files.map { it.key }.toSet()
                val added = files.filter { it.key !in known }
                state.copy(
                    files = state.files + added,
                    selected = state.selected + added.map { it.key },
                    existingNames = state.existingNames + (parentId to names),
                    isWorking = false,
                    message = if (files.isEmpty()) "That folder has no files in it" else state.message
                )
            }
        }
    }

    fun toggleSelection(key: String) {
        _uiState.update { state ->
            val selected = state.selected.toMutableSet()
            if (!selected.add(key)) selected.remove(key)
            state.copy(selected = selected)
        }
    }

    fun selectAll(select: Boolean) {
        _uiState.update { state ->
            state.copy(selected = if (select) state.files.map { it.key }.toSet() else emptySet())
        }
    }

    fun removeFile(key: String) {
        _uiState.update { state ->
            state.copy(
                files = state.files.filter { it.key != key },
                selected = state.selected - key
            )
        }
    }

    fun setExtension(extension: String) {
        val cleaned = extension.trim().trimStart('.').filter { it.isLetterOrDigit() }.lowercase()
        _uiState.update { it.copy(targetExtension = cleaned) }
    }

    fun setAutoSuffix(enabled: Boolean) {
        _uiState.update { it.copy(autoSuffix = enabled) }
    }

    // ---------------------------------------------------------------- rename

    fun renameSelected() {
        val state = _uiState.value
        if (state.isWorking) return
        val plan = RenamePlanner.build(
            files = state.files,
            selected = state.selected,
            extension = state.targetExtension,
            existingNames = state.existingNames,
            autoSuffix = state.autoSuffix
        ).filter { it.changed && !it.conflict }

        if (plan.isEmpty()) {
            _uiState.update { it.copy(message = "Nothing to rename - pick a different extension") }
            return
        }

        viewModelScope.launch {
            val app = getApplication<Application>()
            _uiState.update {
                it.copy(isWorking = true, progressDone = 0, progressTotal = plan.size, report = null)
            }

            val items = ArrayList<HistoryItem>(plan.size)
            plan.forEachIndexed { index, row ->
                val result = FileRenamer.rename(app, row.file.uri, row.newName)
                items.add(
                    HistoryItem(
                        oldUri = row.file.key,
                        newUri = result.newUri?.toString(),
                        oldName = row.oldName,
                        newName = row.newName,
                        success = result.success,
                        error = result.error
                    )
                )
                _uiState.update { it.copy(progressDone = index + 1) }
            }

            val operation = HistoryOperation(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                extension = FileNameUtils.normaliseExtension(state.targetExtension),
                items = items
            )
            withContext(Dispatchers.IO) { store.append(operation) }

            _uiState.update { current ->
                current.copy(
                    files = applyResults(current.files, items),
                    existingNames = applyNameResults(current.existingNames, current.files, items),
                    isWorking = false,
                    report = operation,
                    history = store.load()
                )
            }
        }
    }

    // ---------------------------------------------------------------- undo

    fun undoOperation(operationId: String) {
        if (_uiState.value.isWorking) return
        val operation = store.load().firstOrNull { it.id == operationId } ?: return
        val reversible = operation.items.filter { it.success && it.newUri != null }
        if (reversible.isEmpty()) return

        viewModelScope.launch {
            val app = getApplication<Application>()
            _uiState.update {
                it.copy(isWorking = true, progressDone = 0, progressTotal = reversible.size, report = null)
            }

            val items = ArrayList<HistoryItem>(reversible.size)
            reversible.forEachIndexed { index, item ->
                val result = FileRenamer.rename(app, Uri.parse(item.newUri), item.oldName)
                items.add(
                    HistoryItem(
                        oldUri = item.newUri ?: item.oldUri,
                        newUri = result.newUri?.toString(),
                        oldName = item.newName,
                        newName = item.oldName,
                        success = result.success,
                        error = result.error
                    )
                )
                _uiState.update { it.copy(progressDone = index + 1) }
            }

            withContext(Dispatchers.IO) { store.markUndone(operationId) }

            _uiState.update { current ->
                current.copy(
                    files = applyResults(current.files, items),
                    existingNames = applyNameResults(current.existingNames, current.files, items),
                    isWorking = false,
                    history = store.load(),
                    message = if (items.all { it.success }) {
                        "Undone - ${items.size} file(s) restored"
                    } else {
                        "Undone ${items.count { it.success }}/${items.size} file(s)"
                    }
                )
            }
        }
    }

    // ---------------------------------------------------------------- history

    fun refreshHistory() {
        _uiState.update { it.copy(history = store.load()) }
    }

    fun clearHistory() {
        store.clear()
        _uiState.update { it.copy(history = emptyList()) }
    }

    fun dismissReport() {
        _uiState.update { it.copy(report = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ---------------------------------------------------------------- helpers

    private fun readSiblingNames(files: List<SelectedFile>): Map<String, Set<String>> {
        val app = getApplication<Application>()
        val result = HashMap<String, Set<String>>()
        files.forEach { file ->
            val key = file.parentKey ?: return@forEach
            if (result.containsKey(key)) return@forEach
            FileRenamer.siblingNames(app, file.uri)?.let { result[key] = it }
        }
        return result
    }

    private fun applyResults(files: List<SelectedFile>, items: List<HistoryItem>): List<SelectedFile> {
        val byOldUri = items.filter { it.success && it.newUri != null }.associateBy { it.oldUri }
        return files.map { file ->
            val item = byOldUri[file.key] ?: return@map file
            file.copy(uri = Uri.parse(item.newUri), name = item.newName)
        }
    }

    private fun applyNameResults(
        existing: Map<String, Set<String>>,
        before: List<SelectedFile>,
        items: List<HistoryItem>
    ): Map<String, Set<String>> {
        val updated = HashMap(existing)
        items.filter { it.success }.forEach { item ->
            val parentKey = before.firstOrNull { it.key == item.oldUri }?.parentKey ?: return@forEach
            val names = (updated[parentKey] ?: emptySet()).toMutableSet()
            names.remove(item.oldName)
            names.add(item.newName)
            updated[parentKey] = names
        }
        return updated
    }
}

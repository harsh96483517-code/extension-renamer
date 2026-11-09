package com.harsh.extensionrenamer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(
    val oldUri: String,
    val newUri: String?,
    val oldName: String,
    val newName: String,
    val success: Boolean,
    val error: String?
)

data class HistoryOperation(
    val id: String,
    val timestamp: Long,
    val extension: String,
    val items: List<HistoryItem>,
    val undone: Boolean = false
) {
    val successCount: Int get() = items.count { it.success }
    val failureCount: Int get() = items.count { !it.success }
    val canUndo: Boolean get() = !undone && items.any { it.success && it.newUri != null }
}

/** Persists rename operations as JSON in the app's private storage so undo survives restarts. */
class HistoryStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "rename_history.json")
    private val lock = Any()

    fun load(): List<HistoryOperation> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        try {
            val array = JSONArray(file.readText())
            val result = ArrayList<HistoryOperation>(array.length())
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { result.add(parse(it)) }
            }
            result
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun save(operations: List<HistoryOperation>) {
        synchronized(lock) {
            try {
                val array = JSONArray()
                operations.forEach { array.put(serialise(it)) }
                file.writeText(array.toString())
            } catch (_: Throwable) {
                // History is a convenience; never let a write failure break a rename.
            }
        }
    }

    fun append(operation: HistoryOperation) {
        save((listOf(operation) + load()).take(MAX_OPERATIONS))
    }

    fun clear() {
        save(emptyList())
    }

    fun markUndone(id: String) {
        save(load().map { if (it.id == id) it.copy(undone = true) else it })
    }

    private fun serialise(operation: HistoryOperation): JSONObject {
        val items = JSONArray()
        operation.items.forEach { item ->
            items.put(
                JSONObject().apply {
                    put("oldUri", item.oldUri)
                    put("newUri", item.newUri ?: JSONObject.NULL)
                    put("oldName", item.oldName)
                    put("newName", item.newName)
                    put("success", item.success)
                    put("error", item.error ?: JSONObject.NULL)
                }
            )
        }
        return JSONObject().apply {
            put("id", operation.id)
            put("timestamp", operation.timestamp)
            put("extension", operation.extension)
            put("undone", operation.undone)
            put("items", items)
        }
    }

    private fun parse(json: JSONObject): HistoryOperation {
        val itemsArray = json.optJSONArray("items") ?: JSONArray()
        val items = ArrayList<HistoryItem>(itemsArray.length())
        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(i) ?: continue
            items.add(
                HistoryItem(
                    oldUri = item.optString("oldUri"),
                    newUri = if (item.isNull("newUri")) null else item.optString("newUri"),
                    oldName = item.optString("oldName"),
                    newName = item.optString("newName"),
                    success = item.optBoolean("success"),
                    error = if (item.isNull("error")) null else item.optString("error")
                )
            )
        }
        return HistoryOperation(
            id = json.optString("id"),
            timestamp = json.optLong("timestamp"),
            extension = json.optString("extension"),
            items = items,
            undone = json.optBoolean("undone")
        )
    }

    private companion object {
        const val MAX_OPERATIONS = 100
    }
}

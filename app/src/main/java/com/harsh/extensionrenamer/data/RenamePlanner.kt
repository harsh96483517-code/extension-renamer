package com.harsh.extensionrenamer.data

import com.harsh.extensionrenamer.util.FileNameUtils

/** One row of the preview list. */
data class PreviewRow(
    val file: SelectedFile,
    val oldName: String,
    val newName: String,
    val changed: Boolean,
    /** Target name already exists in the folder and auto-numbering is off. */
    val conflict: Boolean,
    /** False when the folder listing could not be read, so clashes cannot be detected. */
    val verified: Boolean
)

/**
 * Works out the final name of every selected file before anything is touched on disk.
 *
 * Clash handling: a candidate name that already exists in the folder (or that another file in
 * the same batch already claimed) is either flagged as a conflict - and skipped - or, when
 * auto-numbering is on, turned into "name (1).ext", "name (2).ext" and so on.
 */
object RenamePlanner {

    fun build(
        files: List<SelectedFile>,
        selected: Set<String>,
        extension: String,
        existingNames: Map<String, Set<String>>,
        autoSuffix: Boolean
    ): List<PreviewRow> {
        val ext = FileNameUtils.normaliseExtension(extension)
        val rows = ArrayList<PreviewRow>(files.size)
        val takenInFolder = HashMap<String, MutableSet<String>>()

        for (file in files) {
            if (file.key !in selected) continue

            val folder = file.parentKey ?: file.key
            val siblings = existingNames[folder]
            val taken = takenInFolder.getOrPut(folder) {
                siblings?.mapTo(HashSet()) { it.lowercase() } ?: HashSet()
            }

            var candidate = FileNameUtils.withExtension(file.name, ext)
            var conflict = false

            val targetIsSame = candidate.equals(file.name, ignoreCase = true)
            val targetTaken = taken.contains(candidate.lowercase())

            if (!targetIsSame && targetTaken) {
                if (autoSuffix) {
                    candidate = uniqueName(FileNameUtils.baseName(file.name), ext, taken)
                } else {
                    conflict = true
                }
            }

            taken.add(candidate.lowercase())
            rows.add(
                PreviewRow(
                    file = file,
                    oldName = file.name,
                    newName = candidate,
                    changed = candidate != file.name,
                    conflict = conflict,
                    verified = siblings != null
                )
            )
        }
        return rows
    }

    private fun uniqueName(base: String, ext: String, taken: Set<String>): String {
        var index = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext"
            if (!taken.contains(candidate.lowercase())) return candidate
            index++
        }
    }
}

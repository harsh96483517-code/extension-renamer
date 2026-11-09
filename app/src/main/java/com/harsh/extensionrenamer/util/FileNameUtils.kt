package com.harsh.extensionrenamer.util

/** Helpers for splitting a display name into base name + extension. */
object FileNameUtils {

    /** "holiday.tar.gz" -> "holiday.tar";  ".nomedia" -> ".nomedia" (dot-files keep their name). */
    fun baseName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /** "holiday.tar.gz" -> "gz";  "README" -> "" */
    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1) else ""
    }

    /** Replaces only the extension, keeping the rest of the file name untouched. */
    fun withExtension(name: String, extension: String): String {
        val clean = normaliseExtension(extension)
        val base = baseName(name)
        return if (clean.isEmpty()) base else "$base.$clean"
    }

    /** Strips dots/spaces and lowercases, so "  .PNG " becomes "png". */
    fun normaliseExtension(extension: String): String =
        extension.trim().trimStart('.').filter { it.isLetterOrDigit() }.lowercase()

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "-"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${bytes} B" else String.format("%.1f %s", value, units[unit])
    }
}

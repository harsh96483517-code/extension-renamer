package com.harsh.extensionrenamer.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * All filesystem work goes through the Storage Access Framework - no MANAGE_EXTERNAL_STORAGE,
 * no WRITE_EXTERNAL_STORAGE, no direct java.io.File access.
 */
object FileRenamer {

    data class RenameResult(
        val success: Boolean,
        val newUri: Uri?,
        val error: String?
    )

    /** Reads name, size and mime type of a document URI. */
    fun queryMeta(context: Context, uri: Uri): SelectedFile? {
        return try {
            val resolver = context.contentResolver
            var name: String? = null
            var size = 0L
            var mime = resolver.getType(uri)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                    if (!cursor.isNull(2)) mime = cursor.getString(2)
                }
            }
            val displayName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: return null
            SelectedFile(
                uri = uri,
                name = displayName,
                size = size,
                mimeType = mime,
                parentKey = parentDocumentId(uri)
            )
        } catch (t: Throwable) {
            null
        }
    }

    /** Document id of the folder containing [uri], e.g. "primary:Download/photo.jpg" -> "primary:Download". */
    fun parentDocumentId(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val slash = docId.lastIndexOf('/')
            if (slash >= 0) docId.substring(0, slash) else docId.substringBefore(':') + ":"
        } catch (t: Throwable) {
            null
        }
    }

    /** Names of every document next to [uri]; null when the provider refuses the listing. */
    fun siblingNames(context: Context, uri: Uri): Set<String>? {
        val parentId = parentDocumentId(uri) ?: return null
        val authority = uri.authority ?: return null
        return try {
            val childrenUri = DocumentsContract.buildChildDocumentsUri(authority, parentId)
            val names = HashSet<String>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { names.add(it) }
                }
            }
            names
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Renames [uri] to [newName].
     *
     * 1. Tries the provider's native rename, which keeps the document id, timestamps and
     *    any MediaStore entry intact.
     * 2. Falls back to create-copy-delete for providers that do not implement rename
     *    (some cloud and download providers return null). The original is deleted only after
     *    the copy has been flushed successfully.
     */
    suspend fun rename(context: Context, uri: Uri, newName: String): RenameResult =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver

            try {
                val renamed = DocumentsContract.renameDocument(resolver, uri, newName)
                if (renamed != null) return@withContext RenameResult(true, renamed, null)
            } catch (t: Throwable) {
                // fall through to the copy strategy
            }

            try {
                val parentId = parentDocumentId(uri)
                val authority = uri.authority
                if (parentId == null || authority == null) {
                    return@withContext RenameResult(false, null, "This provider does not allow renaming")
                }
                val parentUri = DocumentsContract.buildDocumentUri(authority, parentId)
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val created = DocumentsContract.createDocument(resolver, parentUri, mime, newName)
                    ?: return@withContext RenameResult(
                        false, null,
                        "Cannot create a file here - use \"Select folder\" instead of \"Select files\" for this location"
                    )

                val input = try { resolver.openInputStream(uri) } catch (t: Throwable) { null }
                val output = try { resolver.openOutputStream(created) } catch (t: Throwable) { null }

                var copied = false
                if (input != null && output != null) {
                    try {
                        input.use { source ->
                            output.use { sink ->
                                source.copyTo(sink)
                                sink.flush()
                            }
                        }
                        copied = true
                    } catch (t: Throwable) {
                        copied = false
                    }
                } else {
                    try { input?.close() } catch (_: Throwable) {}
                    try { output?.close() } catch (_: Throwable) {}
                }

                if (!copied) {
                    return@withContext RenameResult(false, null, "Could not copy the file contents")
                }
                try {
                    DocumentsContract.deleteDocument(resolver, uri)
                } catch (_: Throwable) {
                    // The copy exists and is complete; the stale original is harmless.
                }
                RenameResult(true, created, null)
            } catch (t: Throwable) {
                RenameResult(false, null, t.message ?: t.javaClass.simpleName)
            }
        }
}

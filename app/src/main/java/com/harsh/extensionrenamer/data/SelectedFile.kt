package com.harsh.extensionrenamer.data

import android.net.Uri

/**
 * A file the user picked through the Storage Access Framework.
 *
 * @param parentKey the document id of the folder that holds the file. Used to look up the
 *                  names of sibling files so we never overwrite anything.
 */
data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val parentKey: String?
) {
    val key: String get() = uri.toString()
}

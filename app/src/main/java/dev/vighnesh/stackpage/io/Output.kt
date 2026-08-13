package dev.vighnesh.stackpage.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

const val MIME_PDF = "application/pdf"
const val MIME_JPEG = "image/jpeg"
const val MIME_WEBP = "image/webp"
const val MIME_PNG = "image/png"

/** `photo.png` compressed toward 200000 bytes becomes `photo-200kb.jpg`. */
fun compressedFileName(sourceName: String?, targetBytes: Long): String {
    val base = (sourceName ?: "image").substringBeforeLast('.').ifBlank { "image" }
    val kb = (targetBytes / 1000).coerceAtLeast(1)
    return "$base-${kb}kb.jpg"
}

/**
 * Creates a file inside a SAF tree the user picked and returns its Uri, or
 * null if the provider refused. The provider may rename to avoid collisions;
 * that is its right and the returned Uri reflects it.
 */
fun createInTree(context: Context, treeUri: Uri, fileName: String, mimeType: String): Uri? {
    val parent = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )
    return runCatching {
        DocumentsContract.createDocument(context.contentResolver, parent, mimeType, fileName)
    }.getOrNull()
}

/** Hands a finished file to another app via the system share sheet. */
fun shareFile(context: Context, uri: Uri, mimeType: String = "application/pdf") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

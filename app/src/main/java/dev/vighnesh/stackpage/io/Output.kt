package dev.vighnesh.stackpage.io

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Hands a finished file to another app via the system share sheet. */
fun shareFile(context: Context, uri: Uri, mimeType: String = "application/pdf") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

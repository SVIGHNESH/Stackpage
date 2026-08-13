package dev.vighnesh.stackpage.io

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Opens a finished file in whatever viewer the device has, or says it has none. */
fun openFile(context: Context, uri: Uri, mimeType: String = "application/pdf") {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No PDF viewer installed.", Toast.LENGTH_SHORT).show()
    }
}

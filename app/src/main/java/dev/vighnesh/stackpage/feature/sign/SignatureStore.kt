package dev.vighnesh.stackpage.feature.sign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * The signature lives as one transparent PNG in app-private storage, so it
 * is drawn once and reused, never readable by other apps, and deleted the
 * moment the user redraws or the app's data is cleared.
 */
object SignatureStore {

    private fun file(context: Context) = File(context.filesDir, "signature.png")

    fun exists(context: Context): Boolean = file(context).exists()

    fun load(context: Context): Bitmap? =
        file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun loadBytes(context: Context): ByteArray? =
        file(context).takeIf { it.exists() }?.readBytes()

    fun save(context: Context, bitmap: Bitmap) {
        file(context).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    fun delete(context: Context) {
        file(context).delete()
    }
}

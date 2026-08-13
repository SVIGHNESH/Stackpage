package dev.vighnesh.stackpage

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Copies a bundled test asset into the target app's cache dir and hands back a
 * file:// URI. ImageSource only needs a stream from the ContentResolver, and
 * `Uri.fromFile` resolves through it fine, so no FileProvider scaffolding.
 */
object Fixtures {

    /** The app under test - the context whose ContentResolver production code uses. */
    val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun uriFor(assetName: String): Uri {
        val test = InstrumentationRegistry.getInstrumentation().context
        val out = File(targetContext.cacheDir, assetName)
        test.assets.open(assetName).use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(out)
    }
}

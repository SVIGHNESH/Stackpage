package dev.vighnesh.stackpage.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * The tool launcher. Sections of full-width cards, one job per card, never an
 * icon grid: each tool earns a sentence of purpose, and the privacy claim
 * stays in plain words at the bottom where a settings link would normally sit.
 */
@Composable
fun HomeScreen(
    onOpenScan: () -> Unit,
    onOpenStack: () -> Unit,
    onOpenCompress: () -> Unit,
    onOpenConvert: () -> Unit,
    onOpenProtect: () -> Unit,
) {
    // The footer is a bottom bar and the cards scroll: four cards at a large
    // font scale overflow a phone screen, and a pinned-footer Column with no
    // scroll silently clips whatever does not fit.
    Scaffold(bottomBar = { HomeFooter() }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                "Stackpage",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Everyday document tools.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(40.dp))
            Text(
                "TOOLS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ToolCard(
                icon = Icons.Rounded.DocumentScanner,
                title = "Scan to PDF",
                description = "Point the camera at paper; corners and contrast are handled for you.",
                onClick = onOpenScan,
            )
            Spacer(Modifier.height(12.dp))
            ToolCard(
                icon = Icons.Rounded.PictureAsPdf,
                title = "Make a PDF",
                description = "Stack images into one document, in the order you choose.",
                onClick = onOpenStack,
            )
            Spacer(Modifier.height(12.dp))
            ToolCard(
                icon = Icons.Rounded.Compress,
                title = "Compress to a size",
                description = "Shrink images until they fit a target like 200 KB.",
                onClick = onOpenCompress,
            )
            Spacer(Modifier.height(12.dp))
            ToolCard(
                icon = Icons.Rounded.SwapHoriz,
                title = "Convert and resize",
                description = "Change to JPG, PNG, or WebP, with passport and social size presets.",
                onClick = onOpenConvert,
            )
            Spacer(Modifier.height(12.dp))
            ToolCard(
                icon = Icons.Rounded.Lock,
                title = "Protect a PDF",
                description = "Save a copy that opens only with a password you set.",
                onClick = onOpenProtect,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HomeFooter() {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(
            "Everything happens on this device. Nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        // Opening the link is the browser's job; this app keeps holding no
        // network permission of its own.
        Text(
            "Made by Vighnesh Shukla · github.com/svighnesh",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/svighnesh")),
                        )
                    }
                }
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

package dev.vighnesh.stackpage.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Ink on paper.
 *
 * The app turns photos into documents, so the palette is a document's: warm
 * paper in light mode, near-black ink for text, and a single deep ink-blue for
 * anything actionable. Green appears in exactly one place - the export-success
 * state - so it never degrades into decoration.
 *
 * Dynamic colour is deliberately not used. A utility that produces a printed
 * artefact should look the same on every phone, and the wallpaper-derived
 * palettes routinely produce a pastel green primary that reads as "photo
 * gallery", which is the wrong promise.
 */

private val Ink = Color(0xFF1E3A5F)
private val InkLight = Color(0xFF8FB3DE)
private val Paper = Color(0xFFFAFAF8)
private val PaperDim = Color(0xFFF0EFEA)
private val Slate = Color(0xFF334155)
private val Charcoal = Color(0xFF101418)
private val CharcoalRaised = Color(0xFF181D23)
private val Success = Color(0xFF15803D)
private val SuccessDark = Color(0xFF6EE7A0)
private val Danger = Color(0xFFB3261E)
private val DangerDark = Color(0xFFF2B8B5)

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E4F2),
    onPrimaryContainer = Color(0xFF0B1D33),
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1B2531),
    tertiary = Success,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Color(0xFF14181D),
    surface = Paper,
    onSurface = Color(0xFF14181D),
    surfaceVariant = PaperDim,
    onSurfaceVariant = Color(0xFF4A5462),
    surfaceContainer = Color(0xFFF2F1EC),
    surfaceContainerHigh = Color(0xFFECEBE5),
    outline = Color(0xFF9AA3AF),
    outlineVariant = Color(0xFFD9DCE1),
    error = Danger,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = InkLight,
    onPrimary = Color(0xFF0B1D33),
    primaryContainer = Color(0xFF27405F),
    onPrimaryContainer = Color(0xFFD8E4F2),
    secondary = Color(0xFFB6C2D1),
    onSecondary = Color(0xFF1B2531),
    secondaryContainer = Color(0xFF2C3542),
    onSecondaryContainer = Color(0xFFDCE2EA),
    tertiary = SuccessDark,
    onTertiary = Color(0xFF00391B),
    background = Charcoal,
    onBackground = Color(0xFFE6E9ED),
    surface = Charcoal,
    onSurface = Color(0xFFE6E9ED),
    surfaceVariant = CharcoalRaised,
    onSurfaceVariant = Color(0xFFB6BDC7),
    surfaceContainer = Color(0xFF1A2027),
    surfaceContainerHigh = Color(0xFF222831),
    outline = Color(0xFF6D7681),
    outlineVariant = Color(0xFF39414A),
    error = DangerDark,
    onError = Color(0xFF601410),
)

@Composable
fun StackpageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = StackpageTypography,
        content = content,
    )
}

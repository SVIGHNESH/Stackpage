package dev.vighnesh.stackpage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.vighnesh.stackpage.feature.compress.CompressRoute
import dev.vighnesh.stackpage.feature.stack.StackRoute
import dev.vighnesh.stackpage.ui.home.HomeScreen
import dev.vighnesh.stackpage.ui.theme.StackpageTheme

/**
 * Theme plus nav host, nothing else. Screens own their state wiring and the
 * system launchers live in io/, so adding a tool is a route and a card.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StackpageTheme {
                StackpageNavHost()
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val STACK = "stack"
    const val COMPRESS = "compress"
}

@Composable
private fun StackpageNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenStack = { navController.navigate(Routes.STACK) },
                onOpenCompress = { navController.navigate(Routes.COMPRESS) },
            )
        }
        composable(Routes.STACK) {
            StackRoute()
        }
        composable(Routes.COMPRESS) {
            CompressRoute()
        }
    }
}

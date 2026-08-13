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
import dev.vighnesh.stackpage.feature.convert.ConvertRoute
import dev.vighnesh.stackpage.feature.protect.ProtectRoute
import dev.vighnesh.stackpage.feature.scan.ScanRoute
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
    const val CONVERT = "convert"
    const val SCAN = "scan"
    const val PROTECT = "protect"
}

@Composable
private fun StackpageNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenScan = { navController.navigate(Routes.SCAN) },
                onOpenStack = { navController.navigate(Routes.STACK) },
                onOpenCompress = { navController.navigate(Routes.COMPRESS) },
                onOpenConvert = { navController.navigate(Routes.CONVERT) },
                onOpenProtect = { navController.navigate(Routes.PROTECT) },
            )
        }
        composable(Routes.STACK) {
            StackRoute()
        }
        composable(Routes.COMPRESS) {
            CompressRoute()
        }
        composable(Routes.CONVERT) {
            ConvertRoute()
        }
        composable(Routes.PROTECT) {
            ProtectRoute()
        }
        composable(Routes.SCAN) {
            ScanRoute(
                onScanned = {
                    // Scanned pages land in the stack tool; scan drops out of
                    // the back stack so back from the stack returns home.
                    navController.navigate(Routes.STACK) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
    }
}

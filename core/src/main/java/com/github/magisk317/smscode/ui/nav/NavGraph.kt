package com.github.magisk317.smscode.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.github.magisk317.smscode.ui.home.MainScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import kotlinx.serialization.Serializable

@Serializable
object MainRoute

@Serializable
object OverviewRoute

@Serializable
object SettingsRoute

@Serializable
object RecordsRoute

@Serializable
object AppBlockRoute

@Serializable
object AppConfigRoute

@Composable
fun SmsCodeNavHost(
    navController: NavHostController,
    onBack: () -> Unit,
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
) {
    NavHost(
        navController = navController,
        startDestination = MainRoute,
        modifier = modifier,
    ) {
        composable<MainRoute> {
            MainScreen(
                initialTab = initialTab,
                onInitialTabConsumed = onInitialTabConsumed,
                hazeState = hazeState,
                hazeStyle = hazeStyle,
            )
        }
    }
}

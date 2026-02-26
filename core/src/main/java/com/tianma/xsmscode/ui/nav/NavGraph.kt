package com.tianma.xsmscode.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tianma.xsmscode.ui.home.MainScreen
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
object FaqRoute

@Serializable
object InterceptRoute

@Serializable
object RecordsRoute

@Serializable
object AppBlockRoute

@Serializable
object SendersRoute

@Serializable
object AdvancedRoute

@Serializable
data class SenderConfigRoute(val id: Long, val type: Int)

@Serializable
data class RulesRoute(val senderId: Long = 0)

@Serializable
data class RuleConfigRoute(val id: Long = 0)

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

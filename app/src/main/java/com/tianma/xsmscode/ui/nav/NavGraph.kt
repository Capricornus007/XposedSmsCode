package com.tianma.xsmscode.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tianma.xsmscode.ui.home.MainScreen
import com.tianma.xsmscode.ui.rule.edit.RuleEditScreen
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
object FCMRoute

@Serializable
data class RuleEditRoute(val editType: Int, val ruleId: Long? = null)

@Serializable
object RecordsRoute

@Serializable
object AppBlockRoute

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
                onNavigateToRuleEdit = { type, rule ->
                    navController.navigate(RuleEditRoute(editType = type, ruleId = rule?.id))
                },
                initialTab = initialTab,
                onInitialTabConsumed = onInitialTabConsumed,
                hazeState = hazeState,
                hazeStyle = hazeStyle,
            )
        }

        composable<RuleEditRoute> { backStackEntry ->
            val route: RuleEditRoute = backStackEntry.toRoute()
            RuleEditScreen(
                ruleEditType = route.editType,
                initialRuleId = route.ruleId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

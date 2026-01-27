package com.tianma.xsmscode.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tianma.xsmscode.ui.home.ComposeSettingsScreen
import com.tianma.xsmscode.ui.rule.list.RuleListScreen
import com.tianma.xsmscode.ui.rule.edit.RuleEditScreen
import com.tianma.xsmscode.ui.record.CodeRecordScreen
import com.tianma.xsmscode.ui.block.AppBlockScreen
import kotlinx.serialization.Serializable

@Serializable
object SettingsRoute

@Serializable
object RulesListRoute

@Serializable
data class RuleEditRoute(
    val editType: Int,
    val ruleId: Long? = null
)

@Serializable
object RecordsRoute

@Serializable
object AppBlockRoute

@Composable
fun SmsCodeNavHost(
    navController: NavHostController,
    onBack: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = SettingsRoute
    ) {
        composable<SettingsRoute> {
            ComposeSettingsScreen(
                onNavigateToRules = { navController.navigate(RulesListRoute) },
                onNavigateToRecords = { navController.navigate(RecordsRoute) },
                onNavigateToAppBlock = { navController.navigate(AppBlockRoute) }
            )
        }
        
        composable<RulesListRoute> {
            RuleListScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEdit = { type, rule ->
                    navController.navigate(RuleEditRoute(editType = type, ruleId = rule?.id))
                }
            )
        }
        
        composable<RuleEditRoute> { backStackEntry ->
            val route: RuleEditRoute = backStackEntry.toRoute()
            RuleEditScreen(
                ruleEditType = route.editType,
                initialRuleId = route.ruleId,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable<RecordsRoute> {
            CodeRecordScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable<AppBlockRoute> {
            AppBlockScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

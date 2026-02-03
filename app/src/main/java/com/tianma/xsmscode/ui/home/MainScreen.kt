package com.tianma.xsmscode.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.ui.block.AppBlockScreen
import com.tianma.xsmscode.ui.faq.FaqScreen
import com.tianma.xsmscode.ui.nav.*
import com.tianma.xsmscode.ui.record.CodeRecordScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

@Immutable
data class TabItem<T : Any>(val label: String, val icon: ImageVector, val route: T)

@Composable
fun MainScreen(
    onNavigateToRuleEdit: (Int, SmsCodeRule?) -> Unit,
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = listOf(
        TabItem(stringResource(R.string.tab_overview), Icons.Default.Home, OverviewRoute),
        TabItem(stringResource(R.string.tab_blacklist), Icons.Default.Widgets, AppBlockRoute),
        TabItem(stringResource(R.string.tab_records), Icons.Default.History, RecordsRoute),
        TabItem(stringResource(R.string.tab_faq), Icons.AutoMirrored.Filled.Help, FaqRoute),
        TabItem(stringResource(R.string.tab_settings), Icons.Default.Settings, SettingsRoute),
    )
    val selectedIndex = tabs.indexOfFirst { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    }.coerceAtLeast(0)
    var lastSelectedIndex by remember { mutableStateOf(selectedIndex) }
    val slideDirection = if (selectedIndex >= lastSelectedIndex) 1 else -1
    LaunchedEffect(selectedIndex) { lastSelectedIndex = selectedIndex }

    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600

    LaunchedEffect(initialTab) {
        when (initialTab) {
            is OverviewRoute -> navController.navigate(OverviewRoute)
            is AppBlockRoute -> navController.navigate(AppBlockRoute)
            is FaqRoute -> navController.navigate(FaqRoute)
            is RecordsRoute -> navController.navigate(RecordsRoute)
            is SettingsRoute -> navController.navigate(SettingsRoute)
            else -> Unit
        }
        if (initialTab != null) {
            onInitialTabConsumed?.invoke()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isCompact) {
                NavigationRail(
                    header = {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    },
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                        NavigationRailItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = selected,
                            alwaysShowLabel = false,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = OverviewRoute,
                    enterTransition = {
                        val initialIndex = tabs.indexOfFirst { tab ->
                            initialState.destination.hierarchy.any { it.hasRoute(tab.route::class) }
                        }
                        val targetIndex = tabs.indexOfFirst { tab ->
                            targetState.destination.hierarchy.any { it.hasRoute(tab.route::class) }
                        }
                        val direction = if (targetIndex >= initialIndex) 1 else -1

                        slideInHorizontally(
                            animationSpec = tween(300),
                            initialOffsetX = { fullWidth -> direction * fullWidth },
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        val initialIndex = tabs.indexOfFirst { tab ->
                            initialState.destination.hierarchy.any { it.hasRoute(tab.route::class) }
                        }
                        val targetIndex = tabs.indexOfFirst { tab ->
                            targetState.destination.hierarchy.any { it.hasRoute(tab.route::class) }
                        }
                        val direction = if (targetIndex >= initialIndex) 1 else -1

                        slideOutHorizontally(
                            animationSpec = tween(300),
                            targetOffsetX = { fullWidth -> -direction * fullWidth },
                        ) + fadeOut(animationSpec = tween(300))
                    },
                ) {
                    composable<OverviewRoute> {
                        OverviewScreen(hazeState = hazeState, hazeStyle = hazeStyle)
                    }
                    composable<AppBlockRoute> {
                        AppBlockScreen(hazeState = hazeState, hazeStyle = hazeStyle, onBack = null)
                    }
                    composable<FaqRoute> {
                        FaqScreen(hazeState = hazeState, hazeStyle = hazeStyle)
                    }
                    composable<RecordsRoute> {
                        CodeRecordScreen(hazeState = hazeState, hazeStyle = hazeStyle, onBack = null)
                    }
                    composable<SettingsRoute> {
                        ComposeSettingsScreen(
                            hazeState = hazeState,
                            hazeStyle = hazeStyle,
                            onExit = { /* In tab, ignore exit */ },
                        )
                    }
                }
            }
        }

        if (isCompact) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .hazeEffect(hazeState, hazeStyle)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            ),
                            alwaysShowLabel = false,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

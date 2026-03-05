package com.github.magisk317.smscode.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.github.magisk317.smscode.common.constant.TransitionConst
import com.github.magisk317.smscode.common.utils.Utils
import com.github.magisk317.smscode.core.BuildConfig
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.ui.nav.*
import com.github.magisk317.smscode.ui.record.CodeRecordScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import android.os.SystemClock
import android.widget.Toast
import org.koin.compose.viewmodel.koinViewModel

@Immutable
data class TabItem<T : Any>(val label: String, val icon: ImageVector, val route: T)

private const val TAB_DOUBLE_TAP_REFRESH_WINDOW_MS = 350L

@Composable
@Suppress("CyclomaticComplexMethod")
fun MainScreen(
    initialTab: Any? = null,
    onInitialTabConsumed: (() -> Unit)? = null,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
) {
    val context = LocalContext.current
    val isTransitionBuild = BuildConfig.IS_TRANSITION_BUILD
    val isLiteBuild = BuildConfig.IS_LITE_BUILD
    val isRestrictedBuild = isTransitionBuild || isLiteBuild
    val navController = rememberNavController()
    val appConfigViewModel: AppConfigViewModel = koinViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = if (isLiteBuild) {
        listOf(
            TabItem(stringResource(R.string.tab_overview), Icons.Default.Home, OverviewRoute),
            TabItem(stringResource(R.string.tab_advanced), Icons.Default.Tune, AdvancedRoute),
            TabItem(stringResource(R.string.tab_settings), Icons.Default.Settings, SettingsRoute),
        )
    } else {
        listOf(
            TabItem(stringResource(R.string.tab_overview), Icons.Default.Home, OverviewRoute),
            TabItem(stringResource(R.string.tab_blacklist), Icons.Default.Widgets, AppBlockRoute),
            TabItem(stringResource(R.string.tab_records), Icons.Default.History, RecordsRoute),
            TabItem(stringResource(R.string.tab_advanced), Icons.Default.Tune, AdvancedRoute),
            TabItem(stringResource(R.string.tab_settings), Icons.Default.Settings, SettingsRoute),
        )
    }

    fun resolveTabIndex(destination: NavDestination?): Int {
        if (destination == null) return 0
        val hierarchy = destination.hierarchy
        if (isLiteBuild) {
            return when {
                hierarchy.any { it.hasRoute(OverviewRoute::class) } -> 0
                hierarchy.any { it.hasRoute(AdvancedRoute::class) } ||
                    hierarchy.any { it.hasRoute(InterceptRoute::class) } ||
                    hierarchy.any { it.hasRoute(RulesRoute::class) } ||
                    hierarchy.any { it.hasRoute(RuleConfigRoute::class) } -> 1

                hierarchy.any { it.hasRoute(SettingsRoute::class) } -> 2
                else -> 0
            }
        }
        return when {
            hierarchy.any { it.hasRoute(OverviewRoute::class) } -> 0
            hierarchy.any { it.hasRoute(AppNotifySenderBindingRoute::class) } -> 1
            hierarchy.any { it.hasRoute(AppForwardFilterRoute::class) } -> 1
            hierarchy.any { it.hasRoute(AppConfigDetailRoute::class) } -> 1
            hierarchy.any { it.hasRoute(AppBlockRoute::class) } -> 1
            hierarchy.any { it.hasRoute(RecordsRoute::class) } -> 2
            hierarchy.any { it.hasRoute(AdvancedRoute::class) } ||
                hierarchy.any { it.hasRoute(GlobalForwardFilterRoute::class) } ||
                hierarchy.any { it.hasRoute(WebUiConfigRoute::class) } ||
                hierarchy.any { it.hasRoute(InterceptRoute::class) } ||
                hierarchy.any { it.hasRoute(SendersRoute::class) } ||
                hierarchy.any { it.hasRoute(SenderConfigRoute::class) } ||
                hierarchy.any { it.hasRoute(SenderNotifyScopeRoute::class) } ||
                hierarchy.any { it.hasRoute(SenderForwardFilterRoute::class) } ||
                hierarchy.any { it.hasRoute(RulesRoute::class) } ||
                hierarchy.any { it.hasRoute(RuleConfigRoute::class) } ||
                hierarchy.any { it.hasRoute(NotificationRulesRoute::class) } ||
                hierarchy.any { it.hasRoute(AppConfigRoute::class) } -> 3
            hierarchy.any { it.hasRoute(SettingsRoute::class) } -> 4
            else -> 0
        }
    }

    fun shouldShowCompactBottomBar(destination: NavDestination?): Boolean {
        if (destination == null) return true
        val hierarchy = destination.hierarchy
        if (isLiteBuild) {
            return hierarchy.any { it.hasRoute(OverviewRoute::class) } ||
                hierarchy.any { it.hasRoute(AdvancedRoute::class) } ||
                hierarchy.any { it.hasRoute(SettingsRoute::class) }
        }
        return hierarchy.any { it.hasRoute(OverviewRoute::class) } ||
            hierarchy.any { it.hasRoute(AppBlockRoute::class) } ||
            hierarchy.any { it.hasRoute(RecordsRoute::class) } ||
            hierarchy.any { it.hasRoute(AdvancedRoute::class) } ||
            hierarchy.any { it.hasRoute(SettingsRoute::class) }
    }

    val selectedIndex = resolveTabIndex(currentDestination)

    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600
    var appBlockRefreshTrigger by remember { mutableIntStateOf(0) }
    var recordsRefreshTrigger by remember { mutableIntStateOf(0) }
    var interceptRefreshTrigger by remember { mutableIntStateOf(0) }
    var settingsRefreshTrigger by remember { mutableIntStateOf(0) }
    val tabLastTapAt = remember { mutableStateMapOf<String, Long>() }

    fun triggerRefreshForTab(route: Any) {
        when (route) {
            is AppBlockRoute -> appBlockRefreshTrigger++
            is RecordsRoute -> recordsRefreshTrigger++
            is InterceptRoute -> interceptRefreshTrigger++
            is AppConfigRoute -> appBlockRefreshTrigger++
            is SettingsRoute -> settingsRefreshTrigger++
            else -> Unit
        }
    }

    fun handleTabClick(tab: TabItem<*>, selected: Boolean) {
        val key = tab.route::class.qualifiedName ?: tab.label
        val now = SystemClock.elapsedRealtime()
        val last = tabLastTapAt[key] ?: 0L
        tabLastTapAt[key] = now

        if (selected) {
            if (now - last <= TAB_DOUBLE_TAP_REFRESH_WINDOW_MS) {
                triggerRefreshForTab(tab.route)
            }
            return
        }

        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(initialTab) {
        when (initialTab) {
            is OverviewRoute -> navController.navigate(OverviewRoute)
            is AppBlockRoute -> if (!isLiteBuild) navController.navigate(AppBlockRoute) else navController.navigate(OverviewRoute)
            is AppConfigRoute -> if (!isLiteBuild) navController.navigate(AppConfigRoute) else navController.navigate(OverviewRoute)
            is InterceptRoute -> navController.navigate(InterceptRoute)
            is RecordsRoute -> if (!isLiteBuild) navController.navigate(RecordsRoute) else navController.navigate(OverviewRoute)
            is SettingsRoute -> navController.navigate(SettingsRoute)
            else -> Unit
        }
        if (initialTab != null) {
            onInitialTabConsumed?.invoke()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
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
                        val selected = tabs.indexOf(tab) == selectedIndex
                        NavigationRailItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = selected,
                            alwaysShowLabel = false,
                            onClick = { handleTabClick(tab, selected) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = OverviewRoute,
                    enterTransition = {
                        val initialIndex = resolveTabIndex(initialState.destination)
                        val targetIndex = resolveTabIndex(targetState.destination)
                        val direction = if (targetIndex >= initialIndex) 1 else -1

                        slideInHorizontally(
                            animationSpec = tween(300),
                            initialOffsetX = { fullWidth -> direction * fullWidth },
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        val initialIndex = resolveTabIndex(initialState.destination)
                        val targetIndex = resolveTabIndex(targetState.destination)
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
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            AppConfigScreen(
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                                onBack = null,
                                onAppClick = { app -> navController.navigate(AppConfigDetailRoute(packageName = app.packageName)) },
                                refreshTrigger = appBlockRefreshTrigger,
                                viewModel = appConfigViewModel,
                            )
                        }
                    }
                    composable<AppConfigRoute> {
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            AppConfigScreen(
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                                onBack = { navController.popBackStack() },
                                onAppClick = { app -> navController.navigate(AppConfigDetailRoute(packageName = app.packageName)) },
                                refreshTrigger = appBlockRefreshTrigger,
                                viewModel = appConfigViewModel,
                            )
                        }
                    }
                    composable<AppConfigDetailRoute> { backStackEntry ->
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            val route = backStackEntry.toRoute<AppConfigDetailRoute>()
                            AppConfigDetailScreen(
                                packageName = route.packageName,
                                onBack = { navController.popBackStack() },
                                onConfigureNotifyChannels = {
                                    navController.navigate(AppNotifySenderBindingRoute(packageName = route.packageName))
                                },
                                onConfigureForwardFilters = {
                                    navController.navigate(AppForwardFilterRoute(packageName = route.packageName))
                                },
                                viewModel = appConfigViewModel,
                            )
                        }
                    }
                    composable<AppNotifySenderBindingRoute> { backStackEntry ->
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            val route = backStackEntry.toRoute<AppNotifySenderBindingRoute>()
                            AppNotifySenderBindingScreen(
                                packageName = route.packageName,
                                onBack = { navController.popBackStack() },
                                viewModel = appConfigViewModel,
                            )
                        }
                    }
                    composable<InterceptRoute> {
                        InterceptScreen(
                            hazeState = hazeState,
                            hazeStyle = hazeStyle,
                            refreshTrigger = interceptRefreshTrigger,
                        )
                    }
                    composable<RecordsRoute> {
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            CodeRecordScreen(
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                                onBack = null,
                                refreshTrigger = recordsRefreshTrigger,
                            )
                        }
                    }
                    composable<AdvancedRoute> {
                        AdvancedScreen(
                            onInterceptClick = { navController.navigate(InterceptRoute) },
                            onForwardClick = {
                                if (isRestrictedBuild) {
                                    Toast.makeText(context, "转发已迁移到信驿 Relay", Toast.LENGTH_SHORT).show()
                                    Utils.showWebPage(context, TransitionConst.TARGET_RELAY_URL)
                                } else {
                                    navController.navigate(SendersRoute)
                                }
                            },
                            onGlobalForwardFilterClick = {
                                if (isRestrictedBuild) {
                                    Toast.makeText(context, "转发过滤已迁移到信驿 Relay", Toast.LENGTH_SHORT).show()
                                    Utils.showWebPage(context, TransitionConst.TARGET_RELAY_URL)
                                } else {
                                    navController.navigate(GlobalForwardFilterRoute)
                                }
                            },
                            onWebUiConfigClick = { navController.navigate(WebUiConfigRoute) },
                        )
                    }
                    composable<GlobalForwardFilterRoute> {
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            GlobalForwardFilterScreen(onBack = { navController.popBackStack() })
                        }
                    }
                    composable<AppForwardFilterRoute> { backStackEntry ->
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            val route = backStackEntry.toRoute<AppForwardFilterRoute>()
                            AppForwardFilterScreen(
                                packageName = route.packageName,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<WebUiConfigRoute> {
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            WebUiConfigScreen(onBack = { navController.popBackStack() })
                        }
                    }
                    composable<SendersRoute> { backStackEntry ->
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            val reopenTypeDialog by backStackEntry.savedStateHandle
                                .getStateFlow("reopen_type_dialog", false)
                                .collectAsStateWithLifecycle()
                            com.github.magisk317.smscode.ui.sender.SenderListScreen(
                                onAddClick = { type -> navController.navigate(SenderConfigRoute(id = 0L, type = type)) },
                                onEditClick = { id -> navController.navigate(SenderConfigRoute(id = id, type = 1)) },
                                forceShowTypeDialog = reopenTypeDialog,
                                onForceShowHandled = {
                                    backStackEntry.savedStateHandle["reopen_type_dialog"] = false
                                }
                            )
                        }
                    }
                    composable<NotificationRulesRoute> {
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            AppConfigScreen(
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                                onBack = { navController.popBackStack() },
                                onAppClick = { app -> navController.navigate(AppConfigDetailRoute(packageName = app.packageName)) },
                                refreshTrigger = appBlockRefreshTrigger,
                                viewModel = appConfigViewModel,
                            )
                        }
                    }
                    composable<SenderConfigRoute> { backStackEntry ->
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            val route = backStackEntry.toRoute<SenderConfigRoute>()
                            com.github.magisk317.smscode.ui.sender.SenderConfigScreen(
                                senderId = route.id,
                                senderTypeArg = route.type,
                                onOpenSenderNotifyScope = { senderId ->
                                    navController.navigate(SenderNotifyScopeRoute(senderId = senderId))
                                },
                                onOpenSenderForwardFilter = { senderId ->
                                    navController.navigate(SenderForwardFilterRoute(senderId = senderId))
                                },
                                onBack = { reopenTypeDialog ->
                                    if (reopenTypeDialog) {
                                        navController.previousBackStackEntry
                                            ?.savedStateHandle
                                            ?.set("reopen_type_dialog", true)
                                    }
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                    composable<SenderNotifyScopeRoute> { backStackEntry ->
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            val route = backStackEntry.toRoute<SenderNotifyScopeRoute>()
                            com.github.magisk317.smscode.ui.sender.SenderNotifyScopeScreen(
                                senderId = route.senderId,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<SenderForwardFilterRoute> { backStackEntry ->
                        if (isRestrictedBuild) {
                            TransitionForwardingDisabledScreen()
                        } else {
                            val route = backStackEntry.toRoute<SenderForwardFilterRoute>()
                            com.github.magisk317.smscode.ui.sender.SenderForwardFilterScreen(
                                senderId = route.senderId,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<RulesRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<RulesRoute>()
                        com.github.magisk317.smscode.ui.rule.RuleListScreen(
                            senderId = route.senderId,
                            onAddClick = { navController.navigate(RuleConfigRoute(id = 0L)) },
                            onEditClick = { id -> navController.navigate(RuleConfigRoute(id = id)) }
                        )
                    }
                    composable<RuleConfigRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<RuleConfigRoute>()
                        com.github.magisk317.smscode.ui.rule.RuleConfigScreen(
                            ruleId = route.id,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<SettingsRoute> {
                        if (isLiteBuild) {
                            LiteSettingsScreen()
                        } else {
                            ComposeSettingsScreen(
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                                onExit = { /* In tab, ignore exit */ },
                                refreshTrigger = settingsRefreshTrigger,
                            )
                        }
                    }
                }
            }
        }

        if (isCompact && shouldShowCompactBottomBar(currentDestination)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = tabs.indexOf(tab) == selectedIndex
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            ),
                            alwaysShowLabel = false,
                            onClick = { handleTabClick(tab, selected) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionForwardingDisabledScreen() {
    val context = LocalContext.current
    val isLiteBuild = BuildConfig.IS_LITE_BUILD
    val title = if (isLiteBuild) "该功能在验证码精简版中已移除" else "该功能已迁移至信驿 Relay"
    val subtitle = if (isLiteBuild) {
        "当前为验证码精简版，仅保留验证码解析与自动填充能力。"
    } else {
        "当前版本为兼容过渡版本，转发能力已停用。"
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { Utils.showWebPage(context, TransitionConst.TARGET_RELAY_URL) },
            ) {
                Text("前往信驿 Relay")
            }
        }
    }
}

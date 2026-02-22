package com.tianma.xsmscode.ui.block

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.ui.common.AppIconImage
import com.tianma.xsmscode.ui.common.LoadingIndicatorTokens
import com.tianma.xsmscode.ui.common.PolygonMorphLoadingIndicator
import com.tianma.xsmscode.ui.common.SessionLoadingRegistry
import com.tianma.xsmscode.ui.common.rememberMinDurationLoading
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppBlockScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: AppBlockViewModel = koinViewModel(),
) {
    val apps by viewModel.appsFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.loadingFlow.collectAsStateWithLifecycle()
    val hideSystemApps by viewModel.hideSystemAppsFlow.collectAsStateWithLifecycle()
    val hasChanges by viewModel.hasChangesFlow.collectAsStateWithLifecycle()
    val currentSortOption by viewModel.sortOptionFlow.collectAsStateWithLifecycle()
    val isAscending by viewModel.isAscendingFlow.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("app_block") }
    var initialLoadingStarted by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var manualRefreshStartedAt by remember { mutableLongStateOf(0L) }
    val showLoading = rememberMinDurationLoading(
        actualLoading = isLoading && shouldShowInitialLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )

    LaunchedEffect(isLoading, shouldShowInitialLoading, initialLoadingStarted) {
        if (!shouldShowInitialLoading) return@LaunchedEffect
        if (isLoading) {
            initialLoadingStarted = true
        } else if (initialLoadingStarted) {
            SessionLoadingRegistry.markShown("app_block")
        }
    }

    LaunchedEffect(isLoading, manualRefreshing) {
        if (manualRefreshing && !isLoading) {
            val elapsed = if (manualRefreshStartedAt > 0L) {
                SystemClock.elapsedRealtime() - manualRefreshStartedAt
            } else {
                LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS
            }
            val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining)
            manualRefreshing = false
            manualRefreshStartedAt = 0L
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            manualRefreshStartedAt = SystemClock.elapsedRealtime()
            manualRefreshing = true
            viewModel.refreshData(force = true)
        }
    }

    // Initial Load
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    // Usage Permission Dialog State
    var showUsagePermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AppBlockViewModel.AppBlockEvent.SaveSuccess -> {
                    Toast.makeText(context, context.getString(R.string.pref_sync_toast), Toast.LENGTH_SHORT).show()
                    onBack?.invoke()
                }

                is AppBlockViewModel.AppBlockEvent.SaveFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.save_failed))
                }

                is AppBlockViewModel.AppBlockEvent.Error -> {
                    snackbarHostState.showSnackbar(event.throwable.message ?: context.getString(R.string.save_failed))
                }

                is AppBlockViewModel.AppBlockEvent.ShowUsageStatsPermission -> {
                    showUsagePermissionDialog = true
                }
            }
        }
    }

    // Search State
    var searchQuery by remember { mutableStateOf("") }

    // Settings Menu State
    var showSettingsMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val showTopDivider by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp + 72.dp // TopBar(64) + SearchBox(72)
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = manualRefreshing,
            onRefresh = {
                manualRefreshStartedAt = SystemClock.elapsedRealtime()
                manualRefreshing = true
                viewModel.refreshData(force = true)
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    isRefreshing = manualRefreshing,
                    state = pullToRefreshState,
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 0.dp)
        ) {
            if (showLoading && !manualRefreshing) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PolygonMorphLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                    contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppInfoItem(
                            appInfo = app,
                            onCheckedChange = { checked -> viewModel.setBlocked(app, checked) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }

        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .hazeEffect(hazeState, hazeStyle) {
                    forceInvalidateOnPreDraw = true
                },
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_block_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.action_sort))
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false },
                        ) {
                            Text(
                                text = stringResource(R.string.action_sort_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_by_label)) },
                                trailingIcon = {
                                    RadioButton(
                                        selected = currentSortOption == AppBlockViewModel.SortOption.LABEL,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOption(AppBlockViewModel.SortOption.LABEL)
                                    showSettingsMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_by_pkg)) },
                                trailingIcon = {
                                    RadioButton(
                                        selected = currentSortOption == AppBlockViewModel.SortOption.PACKAGE,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOption(AppBlockViewModel.SortOption.PACKAGE)
                                    showSettingsMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_by_usage)) },
                                trailingIcon = {
                                    RadioButton(
                                        selected = currentSortOption == AppBlockViewModel.SortOption.USAGE,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOption(AppBlockViewModel.SortOption.USAGE)
                                    showSettingsMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_reverse)) },
                                trailingIcon = {
                                    Checkbox(
                                        checked = !isAscending,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setAscending(!isAscending)
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_hide_system_apps)) },
                                trailingIcon = {
                                    Checkbox(
                                        checked = hideSystemApps,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setHideSystemApps(!hideSystemApps)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.doFilter(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.doFilter("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )

        if (hasChanges) {
            FloatingActionButton(
                onClick = { viewModel.saveData() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            if (isCompact) 80.dp else 16.dp,
                        end = 16.dp,
                    ),
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_accomplish))
            }
        }
    }

    if (showUsagePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showUsagePermissionDialog = false },
            title = { Text(stringResource(R.string.action_sort_by_usage)) },
            text = { Text(stringResource(R.string.usage_permission_prompt)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    showUsagePermissionDialog = false
                    try {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS),
                        )
                    } catch (ignored: Exception) {
                        // Fallback or toast
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUsagePermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun AppInfoItem(
    appInfo: AppInfo,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(appInfo.label ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(appInfo.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            AppIconImage(
                packageName = appInfo.packageName,
                contentDescription = stringResource(R.string.app_icon_description, appInfo.label ?: ""),
            )
        },
        trailingContent = {
            Checkbox(
                checked = appInfo.blocked,
                onCheckedChange = null,
                modifier = Modifier.semantics {
                    contentDescription = context.getString(
                        if (appInfo.blocked) R.string.action_unblock_app else R.string.action_block_app,
                        appInfo.label ?: "",
                    )
                },
            )
        },
        modifier = modifier.toggleable(
            value = appInfo.blocked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
    )
}

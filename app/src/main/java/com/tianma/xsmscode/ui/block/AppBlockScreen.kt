package com.tianma.xsmscode.ui.block

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.ui.common.AppIconImage
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlockScreen(
    onBack: () -> Unit,
    viewModel: AppBlockViewModel = koinViewModel()
) {
    val apps by viewModel.appsFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.loadingFlow.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    // Initial Load
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    // Usage Permission Dialog State
    var showUsagePermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AppBlockViewModel.AppBlockEvent.SaveSuccess -> onBack()
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
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Sort Menu State
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSearchActive) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = { 
                                searchQuery = it
                                viewModel.doFilter(it)
                            },
                            onSearch = { /* Search is instant */ },
                            expanded = true,
                            onExpandedChange = { if (!it) { isSearchActive = false; searchQuery = ""; viewModel.doFilter("") } },
                            placeholder = { Text(stringResource(R.string.action_search)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = { 
                                IconButton(onClick = { 
                                    if (searchQuery.isNotEmpty()) {
                                        searchQuery = ""
                                        viewModel.doFilter("")
                                    } else {
                                        isSearchActive = false; searchQuery = ""; viewModel.doFilter("")
                                    }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                } 
                            }
                        )
                    },
                    expanded = true,
                    onExpandedChange = { if (!it) { isSearchActive = false; searchQuery = ""; viewModel.doFilter("") } }
                ) {
                    // Show valid content inside search view
                    Box(modifier = Modifier.fillMaxSize()) {
                         if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(apps, key = { it.packageName }) { app ->
                                    AppInfoItem(
                                        appInfo = app,
                                        onClick = { viewModel.doItemClicked(app) }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_block_settings)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.action_sort))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                // 1. App Name
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sort_by_label)) },
                                    trailingIcon = {
                                        if (viewModel.currentSortOption == AppBlockViewModel.SortOption.LABEL) {
                                            Icon(
                                                imageVector = if (viewModel.isAscending) androidx.compose.material.icons.Icons.Filled.ArrowDropUp else androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    onClick = { 
                                        viewModel.doSort(AppBlockViewModel.SortOption.LABEL)
                                        showSortMenu = false 
                                    }
                                )
                                // 2. Package Name
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sort_by_pkg)) },
                                    trailingIcon = {
                                        if (viewModel.currentSortOption == AppBlockViewModel.SortOption.PACKAGE) {
                                            Icon(
                                                imageVector = if (viewModel.isAscending) androidx.compose.material.icons.Icons.Filled.ArrowDropUp else androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    onClick = { 
                                        viewModel.doSort(AppBlockViewModel.SortOption.PACKAGE)
                                        showSortMenu = false 
                                    }
                                )
                                // 3. Usage Frequency
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sort_by_usage)) },
                                    trailingIcon = {
                                        if (viewModel.currentSortOption == AppBlockViewModel.SortOption.USAGE) {
                                            Icon(
                                                imageVector = if (viewModel.isAscending) androidx.compose.material.icons.Icons.Filled.ArrowDropUp else androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    onClick = { 
                                        viewModel.doSort(AppBlockViewModel.SortOption.USAGE)
                                        showSortMenu = false 
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.saveData() }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_accomplish))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors()
                )
            }
        }
    ) { padding ->
        // Main Content (only visible when search is NOT active, effectively)
        // But since SearchBar is full screen overlay, this is hidden when search is active.
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                // Ensure list elements have stable keys and minimize recomposition
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(apps, key = { it.packageName }) { app ->
                        AppInfoItem(
                            appInfo = app,
                            onClick = { viewModel.doItemClicked(app) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showUsagePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showUsagePermissionDialog = false },
            title = { Text(stringResource(R.string.action_sort_by_usage)) },
            text = { Text(stringResource(R.string.usage_permission_prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    showUsagePermissionDialog = false
                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (e: Exception) {
                        // Fallback or toast
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsagePermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun AppInfoItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(appInfo.label ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(appInfo.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            AppIconImage(
                packageName = appInfo.packageName,
                contentDescription = stringResource(R.string.app_icon_description, appInfo.label ?: "")
            )
        },
        trailingContent = {
             Checkbox(
                 checked = appInfo.blocked,
                 onCheckedChange = { onClick() },
                 modifier = Modifier.semantics {
                     contentDescription = context.getString(
                         if (appInfo.blocked) R.string.action_unblock_app else R.string.action_block_app,
                         appInfo.label ?: ""
                     )
                 }
             )
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}

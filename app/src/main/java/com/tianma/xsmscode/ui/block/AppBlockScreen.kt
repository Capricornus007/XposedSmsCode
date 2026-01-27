package com.tianma.xsmscode.ui.block

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.data.db.entity.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Initial Load
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

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
                                        isSearchActive = false
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
                    // Suggestions could go here if needed
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sort_by_label_asc)) },
                                    onClick = { 
                                        viewModel.doSort(SortType.LABEL_ASC)
                                        showSortMenu = false 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sort_by_label_desc)) },
                                    onClick = { 
                                        viewModel.doSort(SortType.LABEL_DESC)
                                        showSortMenu = false 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sort_by_pkg_asc)) },
                                    onClick = { 
                                        viewModel.doSort(SortType.PACKAGE_ASC)
                                        showSortMenu = false 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_sort_by_pkg_desc)) },
                                    onClick = { 
                                        viewModel.doSort(SortType.PACKAGE_DESC)
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
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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
}

@Composable
fun AppInfoItem(
    appInfo: AppInfo,
    onClick: () -> Unit
) {
    var iconPainter by remember { mutableStateOf<Painter?>(null) }
    val context = LocalContext.current
    
    LaunchedEffect(appInfo.packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(appInfo.packageName)
                val bitmap = drawableToBitmap(drawable)
                iconPainter = BitmapPainter(bitmap.asImageBitmap())
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    ListItem(
        headlineContent = { Text(appInfo.label ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(appInfo.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            if (iconPainter != null) {
                Image(
                    painter = iconPainter!!, 
                    contentDescription = null, 
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        },
        trailingContent = {
             Checkbox(
                 checked = appInfo.blocked,
                 onCheckedChange = { onClick() }
             )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

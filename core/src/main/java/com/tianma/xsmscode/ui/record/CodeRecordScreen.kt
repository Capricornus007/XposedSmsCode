package com.tianma.xsmscode.ui.record

import android.content.ClipData
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tianma.xsmscode.core.R
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.ui.common.AppIconImage
import com.tianma.xsmscode.ui.common.LoadingIndicatorTokens
import com.tianma.xsmscode.ui.common.PolygonMorphLoadingIndicator
import com.tianma.xsmscode.ui.common.SessionLoadingRegistry
import com.tianma.xsmscode.ui.common.rememberMinDurationLoading
import com.tianma.xsmscode.ui.home.Item
import com.tianma.xsmscode.ui.home.RetentionDialog
import com.tianma.xsmscode.ui.home.SectionHeader
import com.tianma.xsmscode.ui.home.SwitchItem
import com.tianma.xsmscode.ui.home.TextInputDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CodeRecordScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: CodeRecordViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val smsList = uiState.smsList
    val isLoading = uiState.isLoading
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("records") }
    var initialLoadingStarted by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var manualRefreshStartedAt by remember { mutableLongStateOf(0L) }
    val showLoading = rememberMinDurationLoading(
        actualLoading = isLoading && shouldShowInitialLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(isLoading, shouldShowInitialLoading, initialLoadingStarted) {
        if (!shouldShowInitialLoading) return@LaunchedEffect
        if (isLoading) {
            initialLoadingStarted = true
        } else if (initialLoadingStarted) {
            SessionLoadingRegistry.markShown("records")
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
            viewModel.refreshData()
        }
    }

    val clipboard = LocalClipboard.current

    fun copyWithFeedback(label: String, text: String, toastText: String, snackbarText: String) {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText(label, text).toClipEntry())
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            snackbarHostState.showSnackbar(snackbarText)
        }
    }

    // Initial Load
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    // Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var selectedRecordTab by rememberSaveable { mutableIntStateOf(0) } // 0: code, 1: plain

    var historyLimit by remember { mutableStateOf("0") }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showHistoryLimitInput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        historyLimit = AppPreferencesDataStore.getString(context, PrefConst.KEY_HISTORY_LIMIT, "0")
    }

    // Detail Dialog State
    var detailSmsMsg by remember { mutableStateOf<SmsMsg?>(null) }

    // Logic to toggle selection mode
    fun toggleSelection(id: Long) {
        val newSelection = selectedIds.toMutableSet()
        if (newSelection.contains(id)) {
            newSelection.remove(id)
        } else {
            newSelection.add(id)
        }
        selectedIds = newSelection
        if (newSelection.isEmpty()) {
            isSelectionMode = false
        }
    }

    // Back Handler
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    // Move deleteAndUndo outside items block and remember it
    val deleteAndUndo = remember(viewModel, scope, context, snackbarHostState) {
        { target: SmsMsg ->
            viewModel.removeSmsMsg(listOf(target))
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.some_items_removed, 1),
                    actionLabel = context.getString(R.string.revoke),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.restoreSmsMsgList(listOf(target))
                }
            }
        }
    }

    fun deleteSelected() {
        val deleteList = smsList.filter { sms -> sms.id != null && selectedIds.contains(sms.id) }
        if (deleteList.isEmpty()) return

        viewModel.removeSmsMsg(deleteList)
        isSelectionMode = false
        selectedIds = emptySet()

        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.some_items_removed, deleteList.size),
                actionLabel = context.getString(R.string.revoke),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreSmsMsgList(deleteList)
            }
        }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                viewModel.exportRecords(context, uri)
            }
        }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionHeader(text = stringResource(id = R.string.pref_code_records_title))
                SwitchItem(
                    title = stringResource(id = R.string.pref_enable_code_records_title),
                    summary = "",
                    key = PrefConst.KEY_ENABLE_CODE_RECORDS,
                    defaultValue = true,
                )

                Item(
                    title = stringResource(id = R.string.pref_history_limit_title),
                    summary = run {
                        val entries = stringArrayResource(id = R.array.history_limit_entry_list)
                        val values = stringArrayResource(id = R.array.history_limit_value_list)
                        val index = values.indexOf(historyLimit)
                        if (index >= 0) entries[index] else "$historyLimit ${stringResource(R.string.smscode_records)}"
                    },
                ) { showHistoryLimitDialog = true }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showHistoryLimitDialog) {
        RetentionDialog(
            selectedValue = historyLimit,
            onDismiss = { showHistoryLimitDialog = false },
            titleId = R.string.pref_history_limit_title,
            entriesId = R.array.history_limit_entry_list,
            valuesId = R.array.history_limit_value_list,
        ) { value ->
            if (value == "-1") {
                showHistoryLimitInput = true
            } else {
                historyLimit = value
                scope.launch {
                    AppPreferencesDataStore.setString(context, PrefConst.KEY_HISTORY_LIMIT, value)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                }
            }
            showHistoryLimitDialog = false
        }
    }

    if (showHistoryLimitInput) {
        TextInputDialog(
            title = stringResource(id = R.string.history_limit_custom_entry),
            initialValue = if (historyLimit == "0" || historyLimit == "-1") "" else historyLimit,
            onDismiss = { showHistoryLimitInput = false },
        ) { value ->
            if (value.all { it.isDigit() } && value.isNotEmpty()) {
                historyLimit = value
                scope.launch {
                    AppPreferencesDataStore.setString(context, PrefConst.KEY_HISTORY_LIMIT, value)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                }
            }
            showHistoryLimitInput = false
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = manualRefreshing,
            onRefresh = {
                manualRefreshStartedAt = SystemClock.elapsedRealtime()
                manualRefreshing = true
                viewModel.refreshData()
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
            AnimatedContent(
                targetState = Pair(showLoading, smsList),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "CodeRecordState",
            ) { (loading, list) ->
                if (loading && !manualRefreshing && list.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PolygonMorphLoadingIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                        )
                    }
                } else if (list.isEmpty() && !loading) {
                    // Empty View
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.list_empty_prompt),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val codeSmsList = list.filter { !it.smsCode.isNullOrBlank() }
                    val plainSmsList = list.filter { it.smsCode.isNullOrBlank() }
                    val activeSmsList = if (selectedRecordTab == 0) codeSmsList else plainSmsList

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(state = hazeState)
                            .padding(
                                top = topPadding,
                                bottom = bottomPadding,
                                start = 12.dp,
                                end = 12.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = selectedRecordTab == 0,
                                onClick = { selectedRecordTab = 0 },
                                label = {
                                    Text(
                                        text = "${stringResource(R.string.records_column_code_title)}（${codeSmsList.size}）",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = selectedRecordTab == 1,
                                onClick = { selectedRecordTab = 1 },
                                label = {
                                    Text(
                                        text = "${stringResource(R.string.records_column_plain_title)}（${plainSmsList.size}）",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        RecordSplitColumn(
                            title = if (selectedRecordTab == 0) {
                                stringResource(R.string.records_column_code_title)
                            } else {
                                stringResource(R.string.records_column_plain_title)
                            },
                            emptyHint = if (selectedRecordTab == 0) {
                                stringResource(R.string.records_column_code_empty)
                            } else {
                                stringResource(R.string.records_column_plain_empty)
                            },
                            list = activeSmsList,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onToggleSelection = { toggleSelection(it) },
                            onActivateSelection = {
                                isSelectionMode = true
                                toggleSelection(it)
                            },
                            onCopyCode = { smsMsg ->
                                val code = smsMsg.smsCode
                                if (!code.isNullOrEmpty()) {
                                    val message = context.getString(R.string.prompt_sms_code_copied, code)
                                    copyWithFeedback("sms_code", code, message, message)
                                }
                            },
                            onShowDetail = { detailSmsMsg = it },
                            onDelete = { deleteAndUndo(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            scrollBehavior = scrollBehavior,
                            showHeader = false,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(stringResource(R.string.selected_count, selectedIds.size))
                    } else {
                        Text(stringResource(R.string.smscode_records))
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    } else if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val visibleIds = if (selectedRecordTab == 0) {
                                smsList.filter { !it.smsCode.isNullOrBlank() }
                            } else {
                                smsList.filter { it.smsCode.isNullOrBlank() }
                            }.mapNotNull { it.id }.toSet()
                            if (visibleIds.isEmpty()) return@IconButton
                            val allVisibleSelected = visibleIds.all { selectedIds.contains(it) }
                            selectedIds = if (allVisibleSelected) {
                                selectedIds - visibleIds
                            } else {
                                selectedIds + visibleIds
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = { deleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    } else {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.pref_code_records_title),
                            )
                        }
                        IconButton(onClick = {
                            val filename = "SmsCodeRecords_${SimpleDateFormat(
                                "yyyyMMdd_HHmm",
                                Locale.getDefault(),
                            ).format(Date())}.json"
                            exportLauncher.launch(filename)
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_export),
                                contentDescription = stringResource(R.string.action_export_rules),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier
                    .hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )

        val sms = detailSmsMsg
        if (sms != null) {
            RecordDetailOverlay(
                hazeState = hazeState,
                hazeStyle = hazeStyle,
                sms = sms,
                onDismiss = { detailSmsMsg = null },
                onCopy = { label, value, toast ->
                    copyWithFeedback(label, value, toast, toast)
                },
                onDelete = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.some_items_removed, 1),
                        Toast.LENGTH_SHORT,
                    ).show()
                    deleteAndUndo(sms)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordDetailOverlay(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    sms: SmsMsg,
    onDismiss: () -> Unit,
    onCopy: (label: String, value: String, toast: String) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val detailDateFormatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }
    val sender = sms.sender ?: sms.company ?: context.getString(R.string.unknown)
    val time = detailDateFormatter.format(Date(sms.date))
    val content = sms.body.orEmpty()
    val forwardStatusText = when (sms.forwardStatus) {
        SmsMsg.FORWARD_STATUS_SUCCESS -> stringResource(R.string.forward_status_success)
        SmsMsg.FORWARD_STATUS_FAILED -> stringResource(R.string.forward_status_failed)
        else -> stringResource(R.string.forward_status_none)
    }
    val forwardTarget = sanitizeForwardTarget(sms.forwardTarget)
    val forwardTime = if (sms.forwardTime > 0L) detailDateFormatter.format(Date(sms.forwardTime)) else "-"
    val forwardMessage = sms.forwardMessage ?: "-"
    val dismissInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeEffect(hazeState, hazeStyle) { forceInvalidateOnPreDraw = true }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.message_details),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.detail_click_copy_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_sender)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = sender,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val message = context.getString(
                                R.string.prompt_field_copied,
                                context.getString(R.string.detail_sender),
                            )
                            onCopy("sms_sender", sender, message)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_time)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val message = context.getString(
                                R.string.prompt_field_copied,
                                context.getString(R.string.detail_time),
                            )
                            onCopy("sms_time", time, message)
                        },
                    )
                }
                Text(
                    text = "${stringResource(R.string.detail_content)}:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        if (content.isNotEmpty()) {
                            val message = context.getString(
                                R.string.prompt_field_copied,
                                context.getString(R.string.detail_content),
                            )
                            onCopy("sms_body", content, message)
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_forward_status)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = forwardStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_forward_target)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = forwardTarget,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_forward_time)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = forwardTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_forward_message)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = forwardMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
                ButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                    },
                ) {
                    customItem(
                        buttonGroupContent = {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (content.isNotEmpty()) {
                                        val message = context.getString(R.string.prompt_sms_copied)
                                        onCopy("sms_body", content, message)
                                    }
                                    onDismiss()
                                },
                            ) {
                                Text(stringResource(R.string.copy_sms))
                            }
                        },
                        menuContent = { menuState ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_sms)) },
                                onClick = {
                                    if (content.isNotEmpty()) {
                                        val message = context.getString(R.string.prompt_sms_copied)
                                        onCopy("sms_body", content, message)
                                    }
                                    menuState.dismiss()
                                    onDismiss()
                                },
                            )
                        },
                    )
                    customItem(
                        buttonGroupContent = {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onDelete()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) {
                                Text(stringResource(R.string.delete_sms_action))
                            }
                        },
                        menuContent = { menuState ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_sms_action)) },
                                onClick = {
                                    onDelete()
                                    menuState.dismiss()
                                    onDismiss()
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun sanitizeForwardTarget(rawTarget: String?): String {
    if (rawTarget.isNullOrBlank()) return "-"
    val channels = rawTarget.split("|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { segment ->
            val channel = segment.substringBefore(":", segment).trim()
            if (channel.isEmpty()) segment else channel
        }
        .distinct()
    return if (channels.isEmpty()) "-" else channels.joinToString(" | ")
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecordSplitColumn(
    title: String,
    emptyHint: String,
    list: List<SmsMsg>,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onActivateSelection: (Long) -> Unit,
    onCopyCode: (SmsMsg) -> Unit,
    onShowDetail: (SmsMsg) -> Unit,
    onDelete: (SmsMsg) -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    showHeader: Boolean = true,
) {
    val listState = rememberLazyListState()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = list.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            if (list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                ) {
                    items(list, key = { it.id ?: 0 }) { smsMsg ->
                        val isSelected = selectedIds.contains(smsMsg.id)
                        if (isSelectionMode) {
                            CodeRecordItem(
                                smsMsg = smsMsg,
                                isSelectionMode = true,
                                isSelected = isSelected,
                                onClick = { onToggleSelection(smsMsg.id ?: 0) },
                                onLongClick = {},
                                onDetailClick = { onShowDetail(smsMsg) },
                                modifier = Modifier.animateItem(),
                            )
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                    onDelete(smsMsg)
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = if (dismissState.dismissDirection ==
                                            SwipeToDismissBoxValue.StartToEnd
                                        ) {
                                            Alignment.CenterStart
                                        } else {
                                            Alignment.CenterEnd
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.remove),
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                },
                                content = {
                                    CodeRecordItem(
                                        smsMsg = smsMsg,
                                        isSelectionMode = false,
                                        isSelected = false,
                                        onClick = { onCopyCode(smsMsg) },
                                        onLongClick = { onActivateSelection(smsMsg.id ?: 0) },
                                        onDetailClick = { onShowDetail(smsMsg) },
                                        modifier = Modifier.animateItem(),
                                    )
                                },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeRecordItem(
    smsMsg: SmsMsg,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        // Left Side: Icon + App Name
        val fallbackLabel = (smsMsg.company ?: smsMsg.sender ?: stringResource(R.string.unknown))
            .trim()
            .trim('【', '】', '[', ']')
        val appLabel = remember(smsMsg.packageName) {
            val pkg = smsMsg.packageName
            if (pkg.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrNull()
            }
        }
        val displayLabel = appLabel ?: fallbackLabel
        val iconLabel = if (smsMsg.packageName.isNullOrBlank()) {
            fallbackLabel.replace(Regex("[【】\\[\\]]"), "").trim()
        } else {
            null
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(92.dp)
                .padding(end = 16.dp),
        ) {
            AppIconImage(
                packageName = smsMsg.packageName,
                label = iconLabel,
                contentDescription = stringResource(R.string.sms_icon_description),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
            )
        }

        // Right Side
        Column(modifier = Modifier.weight(1f)) {
            // Top Row: Code + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = smsMsg.smsCode ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text(
                    text = dateFormatter.format(Date(smsMsg.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val body = smsMsg.body
            if (!body.isNullOrEmpty()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onDetailClick() },
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            val forwardStatus = when (smsMsg.forwardStatus) {
                SmsMsg.FORWARD_STATUS_SUCCESS -> stringResource(R.string.forward_status_success)
                SmsMsg.FORWARD_STATUS_FAILED -> stringResource(R.string.forward_status_failed)
                else -> stringResource(R.string.forward_status_none)
            }
            Text(
                text = "${stringResource(R.string.detail_forward_status)}: $forwardStatus",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

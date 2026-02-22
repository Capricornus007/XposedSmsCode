package com.tianma.xsmscode.ui.rule.list

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.feature.backup.ImportResult
import com.tianma.xsmscode.feature.backup.ImportWarning
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    onBack: (() -> Unit)? = null,
    onNavigateToEdit: (Int, SmsCodeRule?) -> Unit,
    viewModel: RuleListViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val rules by viewModel.rulesFlow.collectAsStateWithLifecycle()

    var showProgress by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf<Uri?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.eventsFlow, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.eventsFlow.collect { event ->
                when (event) {
                    is RuleListEvent.ShowProgress -> showProgress = event.msg

                    is RuleListEvent.CancelProgress -> showProgress = null

                    is RuleListEvent.ImportDirect -> viewModel.importRules(
                        event.uri,
                        true,
                        context.getString(R.string.importing),
                    )

                    is RuleListEvent.ImportDialogConfirm -> showImportConfirm = event.uri

                    is RuleListEvent.ExportResultEvent -> {
                        val msg = if (event.success) R.string.export_success_simple else R.string.export_failed
                        snackbarHostState.showSnackbar(context.getString(msg))
                    }

                    is RuleListEvent.ImportResultEvent -> {
                        val msg = if (event.result ==
                            ImportResult.SUCCESS
                        ) {
                            R.string.import_succeed
                        } else {
                            R.string.import_failed
                        }
                        snackbarHostState.showSnackbar(context.getString(msg))
                    }

                    is RuleListEvent.ImportWarningEvent -> {
                        val msg = when (event.warning) {
                            ImportWarning.APP_VERSION_MISMATCH -> context.getString(
                                R.string.import_warning_app_version_mismatch,
                            )
                        }
                        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
                    }
                }
            }
        }
    }

    // Import/Export Launchers
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                viewModel.exportRules(rules, context, uri, context.getString(R.string.exporting))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Trigger import confirmation dialog logic via ViewModel or local state
                viewModel.handleArguments(
                    android.os.Bundle().apply {
                        putParcelable(Const.EXTRA_IMPORT_URI, uri)
                    },
                )
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.rule_list)) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "application/json"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            importLauncher.launch(intent)
                        }) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = stringResource(R.string.action_import_rules),
                            )
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                                putExtra(Intent.EXTRA_TITLE, "rules.json")
                            }
                            exportLauncher.launch(intent)
                        }) {
                            Icon(
                                Icons.Default.FileUpload,
                                contentDescription = stringResource(R.string.action_export_rules),
                            )
                        }
                    },
                )
                // Linear Progress Implementation
                AnimatedVisibility(
                    visible = showProgress != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                onNavigateToEdit(Const.EDIT_TYPE_CREATE, SmsCodeRule())
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_rule))
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = rules,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "RuleListState",
            ) { targetRules ->
                if (targetRules.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.rule_list_empty_prompt))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Const.BOTTOM_SPACE_HEIGHT.dp),
                    ) {
                        items(targetRules, key = { it.id ?: 0 }) { rule ->
                            RuleListItem(
                                rule = rule,
                                onClick = { onNavigateToEdit(Const.EDIT_TYPE_EDIT, rule) },
                                onDelete = { viewModel.removeRule(rule) },
                                modifier = Modifier.animateItem(),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // Import Confirm Dialog
    showImportConfirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            title = { Text(stringResource(R.string.import_rules)) },
            text = { Text(stringResource(R.string.import_rules_confirm_msg)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    showImportConfirm = null
                    viewModel.importRules(uri, true, context.getString(R.string.importing))
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListItem(rule: SmsCodeRule, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var showMenu by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = Const.PADDING_MEDIUM.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        content = {
            ListItem(
                headlineContent = { Text(rule.company ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Column {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    ),
                                ) {
                                    append(stringResource(R.string.rule_keyword_prefix))
                                }
                                append(rule.codeKeyword)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    ),
                                ) {
                                    append(stringResource(R.string.rule_regex_prefix))
                                }
                                append(rule.codeRegex)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                modifier = Modifier.clickable(onClick = onClick),
                trailingContent = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.actions),
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit)) },
                                onClick = {
                                    showMenu = false
                                    onClick()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.remove),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                            )
                        }
                    }
                },
            )
        },
    )
}

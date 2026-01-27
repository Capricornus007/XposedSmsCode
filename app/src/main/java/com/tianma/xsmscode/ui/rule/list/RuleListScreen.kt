package com.tianma.xsmscode.ui.rule.list

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import android.widget.Toast
import androidx.compose.material3.*
import com.tianma.xsmscode.feature.backup.ImportResult
import com.tianma.xsmscode.feature.backup.ImportWarning
import com.tianma.xsmscode.common.utils.Utils
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.common.constant.Const

import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    onBack: () -> Unit,
    onNavigateToEdit: (Int, SmsCodeRule?) -> Unit,
    viewModel: RuleListViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val rules by viewModel.rulesLiveData.observeAsState(emptyList())
    
    val showProgress = remember { mutableStateOf<String?>(null) }
    val showImportConfirm = remember { mutableStateOf<Uri?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel.eventsFlow, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.eventsFlow.collect { event ->
                when (event) {
                    is RuleListEvent.ShowProgress -> showProgress.value = event.msg
                    is RuleListEvent.CancelProgress -> showProgress.value = null
                    is RuleListEvent.ImportDirect -> viewModel.importRules(event.uri, true, context.getString(R.string.importing))
                    is RuleListEvent.ImportDialogConfirm -> showImportConfirm.value = event.uri
                    is RuleListEvent.ExportResultEvent -> {
                        val msg = if (event.success) R.string.export_success_simple else R.string.export_failed
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    is RuleListEvent.ImportResultEvent -> {
                        val msg = if (event.result == ImportResult.SUCCESS) R.string.import_succeed else R.string.import_failed
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    is RuleListEvent.ImportWarningEvent -> {
                        val msg = when (event.warning) {
                            ImportWarning.APP_VERSION_MISMATCH -> context.getString(R.string.import_warning_app_version_mismatch)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                viewModel.handleArguments(android.os.Bundle().apply {
                    putParcelable(Const.EXTRA_IMPORT_URI, uri)
                })
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.rule_list)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                        Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.action_import_rules))
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            putExtra(Intent.EXTRA_TITLE, "rules.json")
                        }
                        exportLauncher.launch(intent)
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.action_export_rules))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                onNavigateToEdit(Const.EDIT_TYPE_CREATE, SmsCodeRule()) 
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_rule))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (rules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.rule_list_empty_prompt))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(rules, key = { it.id ?: 0 }) { rule ->
                        RuleListItem(
                            rule = rule,
                            onClick = { onNavigateToEdit(Const.EDIT_TYPE_EDIT, rule) },
                            onDelete = { viewModel.removeRule(rule) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // Progress Dialog
    showProgress.value?.let { msg ->
        AlertDialog(
            onDismissRequest = { /* Non-cancellable */ },
            title = { Text(stringResource(R.string.please_wait)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(msg)
                }
            },
            confirmButton = {}
        )
    }

    // Import Confirm Dialog
    showImportConfirm.value?.let { uri ->
        AlertDialog(
            onDismissRequest = { showImportConfirm.value = null },
            title = { Text(stringResource(R.string.import_rules)) },
            text = { Text(stringResource(R.string.import_rules_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm.value = null
                    viewModel.importRules(uri, true, context.getString(R.string.importing))
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm.value = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun RuleListItem(
    rule: SmsCodeRule,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(rule.company ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { 
            Column {
                Text(text = "${stringResource(R.string.rule_keyword_prefix)}${rule.codeKeyword}", style = MaterialTheme.typography.bodySmall)
                Text(text = "${stringResource(R.string.rule_regex_prefix)}${rule.codeRegex}", style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    )
}

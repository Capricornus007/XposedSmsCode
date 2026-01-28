package com.tianma.xsmscode.ui.record

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.utils.ClipboardUtils
import com.tianma.xsmscode.data.db.entity.SmsMsg
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeRecordScreen(
    onBack: () -> Unit,
    viewModel: CodeRecordViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val smsList = uiState.smsList
    val isLoading = uiState.isLoading
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    // Initial Load
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    // Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

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
    
    // Deletion Logic
    fun deleteSelected() {
        val toDelete = smsList.filter { selectedIds.contains(it.id) }
        viewModel.removeSmsMsg(toDelete)
        isSelectionMode = false
        selectedIds = emptySet()
        
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.some_items_removed, toDelete.size),
                actionLabel = context.getString(R.string.revoke),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreSmsMsgList(toDelete)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            viewModel.exportRecords(context, uri)
        }
    }

    if (detailSmsMsg != null) {
        AlertDialog(
            onDismissRequest = { detailSmsMsg = null },
            title = { Text(stringResource(R.string.message_details)) },
            text = { Text(detailSmsMsg?.body ?: "") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val code = detailSmsMsg?.smsCode
                        if (!code.isNullOrEmpty()) {
                            clipboardManager.setText(AnnotatedString(code))
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.prompt_sms_code_copied, code))
                            }
                        }
                        detailSmsMsg = null
                    }
                ) {
                    Text(stringResource(R.string.copy_smscode))
                }
            },
            dismissButton = {
                 TextButton(
                    onClick = {
                        val body = detailSmsMsg?.body
                        if (!body.isNullOrEmpty()) {
                            clipboardManager.setText(AnnotatedString(body))
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.prompt_sms_copied))
                            }
                        }
                        detailSmsMsg = null
                    }
                ) {
                    Text(stringResource(R.string.copy_sms))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text(stringResource(R.string.selected_count, selectedIds.size))
                    } else {
                        Text(stringResource(R.string.smscode_records))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedIds = emptySet()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val allIds = smsList.mapNotNull { it.id }.toSet()
                            if (selectedIds.size == allIds.size) {
                                selectedIds = emptySet()
                            } else {
                                selectedIds = allIds
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Select All")
                        }
                        IconButton(onClick = { deleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    } else {
                        IconButton(onClick = {
                            val filename = "SmsCodeRecords_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
                            exportLauncher.launch(filename)
                        }) {
                            Icon(painterResource(R.drawable.ic_export), contentDescription = stringResource(R.string.action_export_rules))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading && smsList.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (smsList.isEmpty() && !isLoading) {
                 // Empty View
                 Column(
                     modifier = Modifier.align(Alignment.Center),
                     horizontalAlignment = Alignment.CenterHorizontally
                 ) {
                     Icon(
                         imageVector = Icons.Default.Email,
                         contentDescription = null,
                         modifier = Modifier.size(64.dp),
                         tint = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                     Text(
                         text = stringResource(R.string.list_empty_prompt),
                         style = MaterialTheme.typography.bodyLarge,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                 }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(smsList, key = { it.id ?: 0 }) { smsMsg ->
                        val isSelected = selectedIds.contains(smsMsg.id)
                        CodeRecordItem(
                            smsMsg = smsMsg,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    toggleSelection(smsMsg.id ?: 0)
                                } else {
                                    // Copy Code by default? adapter.itemClicked did copySmsCode.
                                    val code = smsMsg.smsCode
                                    if (!code.isNullOrEmpty()) {
                                        clipboardManager.setText(AnnotatedString(code))
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.prompt_sms_code_copied, code))
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    toggleSelection(smsMsg.id ?: 0)
                                }
                            },
                            onDetailClick = {
                                detailSmsMsg = smsMsg
                            }
                        )
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
    onDetailClick: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        // Left Side: Icon + App Name
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 16.dp)
        ) {
            // Placeholder Icon (Use App Icon if available, else Default)
            Icon(
                imageVector = Icons.Default.Email, // Replace with App Icon loader if available
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (!smsMsg.company.isNullOrBlank()) smsMsg.company!! else smsMsg.sender ?: "Unknown",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right Side
        Column(modifier = Modifier.weight(1f)) {
            // Top Row: Code + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = smsMsg.smsCode ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = dateFormatter.format(Date(smsMsg.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Bottom: Body
            if (!smsMsg.body.isNullOrEmpty()) {
                Text(
                    text = smsMsg.body!!,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onDetailClick() }
                )
            }
        }
    }
}

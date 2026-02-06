package com.tianma.xsmscode.ui.fcm

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.common.utils.SPUtils
import com.tianma.xsmscode.common.utils.Utils
import com.tianma.xsmscode.ui.home.SectionHeader
import com.tianma.xsmscode.ui.home.SwitchItem
import com.tianma.xsmscode.ui.home.Item
import com.tianma.xsmscode.ui.home.TextInputDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FCMScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    var isFcmEnabled by remember { mutableStateOf(false) }
    var serviceAccountJson by remember { mutableStateOf("") }
    var fcmToken by remember { mutableStateOf<String?>(null) }
    var syncGroupId by remember { mutableStateOf("") }
    var showGroupIdDialog by remember { mutableStateOf(false) }

    // File picker for service account JSON
    val jsonPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val jsonContent = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                if (jsonContent != null) {
                    serviceAccountJson = jsonContent
                    scope.launch {
                        SPUtils.setFcmServiceAccountJson(context, jsonContent)
                        AppPreferencesDataStore.syncToSharedPrefs(context)
                        Toast.makeText(context, context.getString(R.string.fcm_service_account_saved), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Init load
    LaunchedEffect(Unit) {
        isFcmEnabled = AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_FCM_ENABLE, false)
        serviceAccountJson = SPUtils.getFcmServiceAccountJson(context) ?: ""
        fcmToken = SPUtils.getFcmToken(context)
        syncGroupId = SPUtils.getSyncGroupId(context)
        if (syncGroupId.isBlank()) {
            syncGroupId = java.util.UUID.randomUUID().toString()
            SPUtils.setSyncGroupId(context, syncGroupId)
        }
    }
    
    // Ensure subscription when enabled
    LaunchedEffect(isFcmEnabled) {
        if (isFcmEnabled) {
            com.tianma.xsmscode.feature.fcm.FCMService.subscribeToSyncGroup(context)
        }
    }

    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
        val isCompact = LocalConfiguration.current.screenWidthDp < 600
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            if (isCompact) Const.BOTTOM_SPACE_HEIGHT.dp else 0.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(bottom = bottomPadding)
                .verticalScroll(scrollState),
        ) {
            Spacer(modifier = Modifier.height(topPadding))

            SectionHeader(text = stringResource(id = R.string.pref_fcm_title))

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
             Toast.makeText(context, context.getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    // Master Switch
    SwitchItem(
        title = stringResource(id = R.string.pref_fcm_enable_title),
        summary = stringResource(id = R.string.pref_fcm_enable_summary),
        key = PrefConst.KEY_FCM_ENABLE,
        defaultValue = false,
        onToggle = { enabled -> 
            if (enabled) {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                com.tianma.xsmscode.feature.fcm.FCMService.subscribeToSyncGroup(context)
            } else {
                com.tianma.xsmscode.feature.fcm.FCMService.unsubscribeFromSyncGroup(context, syncGroupId)
            }
        },
    )

            // Conditional Content
            if (isFcmEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Service Account JSON Upload
                Item(
                    title = stringResource(id = R.string.pref_fcm_service_account_title),
                    summary = if (serviceAccountJson.isNotBlank()) 
                        stringResource(id = R.string.pref_fcm_service_account_configured) 
                    else 
                        stringResource(id = R.string.pref_fcm_service_account_hint),
                ) { jsonPickerLauncher.launch("application/json") }

                Text(
                    text = stringResource(id = R.string.pref_fcm_service_account_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Receiver-only Hint
                if (serviceAccountJson.isBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.fcm_receiver_only_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Group ID Input with Copy
                ListItem(
                    headlineContent = { Text(text = stringResource(id = R.string.pref_fcm_group_id_title)) },
                    supportingContent = { 
                        Text(
                            text = syncGroupId,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                val newId = java.util.UUID.randomUUID().toString()
                                val oldId = syncGroupId
                                syncGroupId = newId
                                scope.launch {
                                    SPUtils.setSyncGroupId(context, newId)
                                    AppPreferencesDataStore.syncToSharedPrefs(context)
                                    // Update subscription
                                    com.tianma.xsmscode.feature.fcm.FCMService.unsubscribeFromSyncGroup(context, oldId)
                                    com.tianma.xsmscode.feature.fcm.FCMService.subscribeToSyncGroup(context)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate"
                                )
                            }
                            IconButton(onClick = {
                                Utils.copyToClipboard(context, syncGroupId)
                                Toast.makeText(context, context.getString(R.string.copy_group_id), Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(id = R.string.copy_group_id)
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { showGroupIdDialog = true }
                )
                
                Text(
                    text = stringResource(id = R.string.pref_fcm_group_id_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                
                // Note about encryption
                Text(
                    text = "消息通过 Group ID 进行端到端加密，确保隐私。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            } else {
                // Disabled State (Greying out optional)
                // Since user asked for "disappear or disabled", hiding is simpler and cleaner.
            }
        }

        // Top Bar
        TopAppBar(
            title = { Text(text = stringResource(id = R.string.tab_fcm)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            scrollBehavior = scrollBehavior,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .hazeEffect(hazeState, hazeStyle) { forceInvalidateOnPreDraw = true },
            windowInsets = WindowInsets.statusBars,
        )
    }

    // Dialogs

    if (showGroupIdDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_fcm_group_id_title),
            initialValue = syncGroupId,
            onDismiss = { showGroupIdDialog = false },
            onConfirm = { value ->
                syncGroupId = value.trim()
                scope.launch {
                    val oldGroupId = SPUtils.getSyncGroupId(context)
                    SPUtils.setSyncGroupId(context, syncGroupId)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                    
                    // Update subscription
                    com.tianma.xsmscode.feature.fcm.FCMService.unsubscribeFromSyncGroup(context, oldGroupId)
                    com.tianma.xsmscode.feature.fcm.FCMService.subscribeToSyncGroup(context)

                    Toast.makeText(context, context.getString(R.string.fcm_group_id_saved), Toast.LENGTH_SHORT).show()
                }
                showGroupIdDialog = false
            }
        )
    }
}

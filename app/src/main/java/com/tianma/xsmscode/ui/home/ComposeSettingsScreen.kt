package com.tianma.xsmscode.ui.home

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.ModuleUtils
import com.tianma.xsmscode.common.utils.PackageUtils
import com.tianma.xsmscode.common.utils.Utils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.common.utils.SPUtils
import com.tianma.xsmscode.data.db.entity.ApkVersion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSettingsScreen(
    onNavigateToRules: () -> Unit,
    onNavigateToRecords: () -> Unit,
    onNavigateToAppBlock: () -> Unit,
    viewModel: SettingsViewModel? = null,
    onExit: () -> Unit = {}
) {
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val settingsViewModel = viewModel ?: if (activityOwner != null) {
        koinViewModel(viewModelStoreOwner = activityOwner)
    } else {
        koinViewModel()
    }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle(0)
    val updateVersion by settingsViewModel.updateVersion.collectAsStateWithLifecycle(null)
    
    val autoInputDelayState = remember { mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT) }
    val retentionTimeState = remember { mutableStateOf(PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT) }
    val smsCodeKeywordsState = remember { mutableStateOf(PrefConst.SMSCODE_KEYWORDS_DEFAULT) }
    
    val showAutoInputDialog = remember { mutableStateOf(false) }
    val showRetentionDialog = remember { mutableStateOf(false) }
    val showSmsTestDialog = remember { mutableStateOf(false) }
    val smsTestInput = remember { mutableStateOf("") }
    val showThemeDialog = remember { mutableStateOf(false) }
    val showDonateDialog = remember { mutableStateOf(false) }
    val showAlipayChoiceDialog = remember { mutableStateOf(false) }
    val showQRCodeDialog = remember { mutableStateOf<Pair<Int, String>?>(null) }
    val showPrivacyPolicyDialog = remember { mutableStateOf(false) }
    val showKeywordsDialog = remember { mutableStateOf(false) }
    var isActivated by remember { mutableStateOf(ModuleUtils.isModuleEnabled()) }

    LaunchedEffect(Unit) {
        autoInputDelayState.value = AppPreferencesDataStore.getString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT)
        retentionTimeState.value = AppPreferencesDataStore.getString(context, PrefConst.KEY_NOTIFICATION_RETENTION_TIME, PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT)
        smsCodeKeywordsState.value = AppPreferencesDataStore.getString(context, PrefConst.KEY_SMSCODE_KEYWORDS, PrefConst.SMSCODE_KEYWORDS_DEFAULT)
        settingsViewModel.setInternalFilesWritable()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            isActivated = ModuleUtils.isModuleEnabled()
            delay(1000L)
            isActivated = ModuleUtils.isModuleEnabled()
        }
    }

    LaunchedEffect(settingsViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            settingsViewModel.eventsFlow.collect { event ->
                when (event) {
                    is SettingsEvent.SmsCodeTestResult -> {
                        val text = if (event.code.isBlank()) {
                            context.getString(R.string.cannot_parse_smscode)
                        } else {
                            context.getString(R.string.current_sms_code, event.code)
                        }
                        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                    }
                    is SettingsEvent.ShowPrivacyPolicy -> {
                        showPrivacyPolicyDialog.value = true
                    }
                    is SettingsEvent.ShowAlipayPacket -> {
                        showDonateDialog.value = true
                    }
                    is SettingsEvent.CheckUpdateError -> {
                        Toast.makeText(context, R.string.check_update_failed, Toast.LENGTH_SHORT).show()
                    }
                    is SettingsEvent.AppAlreadyNewest -> {
                        Toast.makeText(context, R.string.app_already_newest, Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = stringResource(id = R.string.app_name))
                        Text(
                            text = if (isActivated) stringResource(R.string.module_status_active) else stringResource(R.string.module_status_inactive),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActivated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionHeader(text = stringResource(id = R.string.pref_general_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_enable_title),
                summary = stringResource(id = R.string.pref_enable_summary),
                key = PrefConst.KEY_ENABLE,
                defaultValue = true
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_hide_launcher_icon_title),
                summary = stringResource(id = R.string.pref_hide_launcher_icon_summary),
                key = PrefConst.KEY_HIDE_LAUNCHER_ICON,
                defaultValue = false,
                onToggle = { enabled -> settingsViewModel.hideOrShowLauncherIcon(enabled) }
            )
            Item(
                title = stringResource(id = R.string.pref_choose_theme_title),
                summary = stringResource(id = R.string.pref_choose_theme_summary)
            ) { showThemeDialog.value = true }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(text = stringResource(id = R.string.pref_sms_code_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_show_toast_title),
                summary = stringResource(id = R.string.pref_show_toast_summary),
                key = PrefConst.KEY_SHOW_TOAST,
                defaultValue = true
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_copy_to_clipboard_title),
                summary = stringResource(id = R.string.pref_copy_to_clipboard_summary),
                key = PrefConst.KEY_COPY_TO_CLIPBOARD,
                defaultValue = false
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_block_sms_title),
                summary = stringResource(id = R.string.pref_block_sms_summary),
                key = PrefConst.KEY_BLOCK_SMS,
                defaultValue = false
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_deduplicate_sms_title),
                summary = stringResource(id = R.string.pref_deduplicate_sms_summary),
                key = PrefConst.KEY_DEDUPLICATE_SMS,
                defaultValue = false
            )
            Item(
                title = stringResource(id = R.string.pref_smscode_keywords_title),
                summary = stringResource(id = R.string.pref_smscode_keywords_summary)
            ) { showKeywordsDialog.value = true }
            Item(
                title = stringResource(id = R.string.pref_smscode_test_title),
                summary = stringResource(id = R.string.pref_smscode_test_summary)
            ) { showSmsTestDialog.value = true }
            Item(
                title = stringResource(id = R.string.pref_code_rules_title),
                summary = stringResource(id = R.string.pref_code_rules_summary)
            ) { onNavigateToRules() }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(text = stringResource(id = R.string.pref_category_auto_input_title))
            val autoInputEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true)
            SwitchItem(
                title = stringResource(id = R.string.pref_enable_auto_input_code_title),
                summary = stringResource(id = R.string.pref_enable_auto_input_code_summary),
                key = PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
                defaultValue = true,
                stateOverride = autoInputEnabled
            )
            Item(
                title = stringResource(id = R.string.pref_auto_input_code_delay_title),
                summary = stringResource(id = R.string.pref_auto_input_code_delay_summary, autoInputDelayState.value)
            ) { showAutoInputDialog.value = true }
            Item(
                title = stringResource(id = R.string.app_block_settings),
                summary = stringResource(id = R.string.app_block_summary)
            ) { onNavigateToAppBlock() }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(text = stringResource(id = R.string.pref_notification_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_show_code_notification_title),
                summary = stringResource(id = R.string.pref_show_code_notification_summary),
                key = PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                defaultValue = true
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_auto_cancel_notification_title),
                summary = stringResource(id = R.string.pref_auto_cancel_notification_summary),
                key = PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                defaultValue = false
            )
            Item(
                title = stringResource(id = R.string.pref_notification_retention_time_title),
                summary = run {
                    val entries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
                    val values = stringArrayResource(id = R.array.notification_retention_time_list)
                    val index = values.indexOf(retentionTimeState.value)
                    if (index >= 0) entries[index] else retentionTimeState.value
                }
            ) { showRetentionDialog.value = true }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(text = stringResource(id = R.string.pref_code_records_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_enable_code_records_title),
                summary = "",
                key = PrefConst.KEY_ENABLE_CODE_RECORDS,
                defaultValue = true
            )
            Item(
                title = stringResource(id = R.string.pref_entry_code_records_title),
                summary = stringResource(id = R.string.pref_entry_code_records_summary, PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT)
            ) { onNavigateToRecords() }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(text = stringResource(id = R.string.pref_others_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_verbose_log_mode_title),
                summary = stringResource(id = R.string.pref_verbose_log_mode_summary),
                key = PrefConst.KEY_VERBOSE_LOG_MODE,
                defaultValue = false,
                onToggle = { on -> XLog.setLogLevel(if (on) Log.VERBOSE else BuildConfig.LOG_LEVEL) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(text = stringResource(id = R.string.pref_about_title))
            Item(
                title = stringResource(id = R.string.pref_version_title),
                summary = stringResource(id = R.string.pref_version_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            ) { settingsViewModel.checkUpdate() }
            Item(
                title = stringResource(id = R.string.pref_join_qq_group_title),
                summary = stringResource(id = R.string.pref_join_qq_group_summary, Const.QQ_GROUP_URL)
            ) { PackageUtils.joinQQGroup(context) }
            Item(
                title = stringResource(id = R.string.pref_source_code_title),
                summary = stringResource(id = R.string.pref_source_code_summary)
            ) { Utils.showWebPage(context, Const.PROJECT_SOURCE_CODE_URL) }
            Item(
                title = stringResource(id = R.string.pref_donate_by_alipay_title),
                summary = stringResource(id = R.string.dialog_donate_content)
            ) { showDonateDialog.value = true }
            Item(
                title = stringResource(id = R.string.pref_privacy_policy_title),
                summary = ""
            ) { showPrivacyPolicyDialog.value = true }
        }
    }

    // Reuse Dialogs and Helper Components
    if (showAutoInputDialog.value) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_delay_title),
            value = autoInputDelayState,
            onDismiss = { showAutoInputDialog.value = false }
        ) { value ->
            autoInputDelayState.value = value
            scope.launch { AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, value) }
            showAutoInputDialog.value = false
        }
    }

    if (showRetentionDialog.value) {
        RetentionDialog(
            selectedValue = retentionTimeState.value,
            onDismiss = { showRetentionDialog.value = false }
        ) { value ->
            retentionTimeState.value = value
            scope.launch { AppPreferencesDataStore.setString(context, PrefConst.KEY_NOTIFICATION_RETENTION_TIME, value) }
            showRetentionDialog.value = false
        }
    }

    if (showSmsTestDialog.value) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_smscode_test_title),
            value = smsTestInput,
            onDismiss = { showSmsTestDialog.value = false },
            singleLine = false,
            maxLines = 8
        ) { value ->
            smsTestInput.value = value
            settingsViewModel.performSmsCodeTest(value)
            showSmsTestDialog.value = false
        }
    }

    if (showKeywordsDialog.value) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_smscode_keywords_title),
            value = smsCodeKeywordsState,
            onDismiss = { showKeywordsDialog.value = false }
        ) { value ->
            val updated = if (value.isBlank()) PrefConst.SMSCODE_KEYWORDS_DEFAULT else value
            smsCodeKeywordsState.value = updated
            scope.launch { AppPreferencesDataStore.setString(context, PrefConst.KEY_SMSCODE_KEYWORDS, updated) }
            showKeywordsDialog.value = false
        }
    }

    updateVersion?.let { version ->
        UpdateDialog(
            version = version,
            onDismiss = { settingsViewModel.clearUpdateVersion() },
            onUpdateCoolApk = { settingsViewModel.updateFromCoolApk() },
            onUpdateGithub = { settingsViewModel.updateFromGithub() }
        )
    }

    if (showThemeDialog.value) {
        ThemeChooserDialog(
            currentMode = themeMode,
            onDismiss = { showThemeDialog.value = false },
            onThemeSelected = { mode ->
                settingsViewModel.setThemeMode(mode)
                showThemeDialog.value = false
            }
        )
    }

    if (showDonateDialog.value) {
         DonateDialog(
             onDismiss = { showDonateDialog.value = false },
             onAlipay = { showDonateDialog.value = false; showAlipayChoiceDialog.value = true },
             onWechat = {
                 showDonateDialog.value = false
                 showQRCodeDialog.value = Pair(R.drawable.wx, "wechat")
             }
         )
    }

    if (showAlipayChoiceDialog.value) {
        AlipayChoiceDialog(
            onDismiss = { showAlipayChoiceDialog.value = false },
            onQRCode = {
                showAlipayChoiceDialog.value = false
                showQRCodeDialog.value = Pair(R.drawable.alipay, "alipay")
            },
            onToken = {
                showAlipayChoiceDialog.value = false
                PackageUtils.copyAlipayPocketToken(context)
            }
        )
    }

    showQRCodeDialog.value?.let { pair ->
        QRCodeDialog(
            resId = pair.first,
            type = pair.second,
            onDismiss = { showQRCodeDialog.value = null },
            onSave = { Utils.saveImageToGallery(context, pair.first, "${pair.second}_qrcode") }
        )
    }

    if (showPrivacyPolicyDialog.value) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyPolicyDialog.value = false },
            onConfirm = {
                scope.launch { SPUtils.setPrivacyPolicyAccepted(context, true) }
                showPrivacyPolicyDialog.value = false
            },
            onCancel = {
                scope.launch { SPUtils.setPrivacyPolicyAccepted(context, false) }
                showPrivacyPolicyDialog.value = false
                onExit()
            }
        )
    }
}

// Helper Composables (extracted and made standalone)

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun Item(title: String, summary: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotEmpty()) {
                Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SwitchItem(
    title: String,
    summary: String,
    key: String,
    defaultValue: Boolean,
    stateOverride: MutableState<Boolean>? = null,
    onToggle: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checkedState = stateOverride ?: rememberPrefBoolean(key, defaultValue)

    fun toggle(checked: Boolean) {
        checkedState.value = checked
        scope.launch { AppPreferencesDataStore.setBoolean(context, key, checked) }
        onToggle?.invoke(checked)
    }

    Surface(
        onClick = { toggle(!checkedState.value) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (summary.isNotEmpty()) {
                    Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checkedState.value, onCheckedChange = { toggle(it) })
        }
    }
}

@Composable
fun rememberPrefBoolean(key: String, defaultValue: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(defaultValue) }
    LaunchedEffect(key) {
        state.value = AppPreferencesDataStore.getBoolean(context, key, defaultValue)
    }
    return state
}

@Composable
fun TextInputDialog(
    title: String,
    value: MutableState<String>,
    onDismiss: () -> Unit,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    onConfirm: (String) -> Unit
) {
    val text = remember { mutableStateOf(value.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = text.value,
                onValueChange = { text.value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                maxLines = maxLines
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.value) }) {
                Text(stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
fun RetentionDialog(
    selectedValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val entries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
    val values = stringArrayResource(id = R.array.notification_retention_time_list)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.pref_notification_retention_time_title)) },
        text = {
            Column {
                entries.forEachIndexed { index, entry ->
                    val value = values.getOrNull(index) ?: return@forEachIndexed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selectedValue, onClick = { onConfirm(value) })
                        Text(text = entry, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun UpdateDialog(
    version: ApkVersion,
    onDismiss: () -> Unit,
    onUpdateCoolApk: () -> Unit,
    onUpdateGithub: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.pref_version_title)) },
        text = { Text(version.versionInfo ?: "") },
        confirmButton = {
            TextButton(onClick = onUpdateCoolApk) { Text("CoolApk") }
            TextButton(onClick = onUpdateGithub) { Text("GitHub") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
        }
    )
}

@Composable
fun ThemeChooserDialog(
    currentMode: Int,
    onDismiss: () -> Unit,
    onThemeSelected: (Int) -> Unit
) {
    val modes = listOf(
        stringResource(id = R.string.theme_follow_system) to 0,
        stringResource(id = R.string.theme_light) to 1,
        stringResource(id = R.string.theme_dark) to 2
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.pref_choose_theme_title)) },
        text = {
            Column {
                modes.forEach { (label, mode) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == currentMode, onClick = { onThemeSelected(mode) })
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun DonateDialog(onDismiss: () -> Unit, onAlipay: () -> Unit, onWechat: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_donate_title)) },
        text = { Text(stringResource(id = R.string.dialog_donate_content)) },
        confirmButton = {
            TextButton(onClick = onAlipay) { Text(stringResource(id = R.string.dialog_donate_alipay)) }
            TextButton(onClick = onWechat) { Text(stringResource(id = R.string.dialog_donate_wechat)) }
        }
    )
}

@Composable
fun AlipayChoiceDialog(onDismiss: () -> Unit, onQRCode: () -> Unit, onToken: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_donate_alipay)) },
        confirmButton = {
            TextButton(onClick = onQRCode) { Text(stringResource(id = R.string.dialog_donate_alipay_qrcode)) }
            TextButton(onClick = onToken) { Text(stringResource(id = R.string.dialog_donate_alipay_token)) }
        }
    )
}

@Composable
fun QRCodeDialog(resId: Int, type: String, onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == "alipay") stringResource(id = R.string.dialog_donate_alipay) else stringResource(id = R.string.dialog_donate_wechat)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(id = R.string.save_to_gallery)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
        }
    )
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.pref_privacy_policy_title)) },
        text = {
            Text(androidx.core.text.HtmlCompat.fromHtml(stringResource(id = R.string.privacy_dialog_content), androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString())
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(id = R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(id = R.string.cancel)) }
        }
    )
}

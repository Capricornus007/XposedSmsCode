package com.tianma.xsmscode.ui.home

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.ModuleUtils
import com.tianma.xsmscode.common.utils.ModuleActivationStore
import com.tianma.xsmscode.common.utils.PackageUtils
import com.tianma.xsmscode.common.utils.Utils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.common.utils.SPUtils
import com.tianma.xsmscode.data.db.entity.ApkVersion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalView

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
    
    val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()
    val themeMode = themeState.mode
    val updateVersion by settingsViewModel.updateVersion.collectAsStateWithLifecycle(null)
    
    var autoInputDelay by remember { mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT) }
    var retentionTime by remember { mutableStateOf(PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT) }
    var smsCodeKeywords by remember { mutableStateOf(PrefConst.SMSCODE_KEYWORDS_DEFAULT) }
    var showFaqDialog by remember { mutableStateOf(false) }
    
    var showAutoInputDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showSmsTestDialog by remember { mutableStateOf(false) }
    var smsTestInput by remember { mutableStateOf("") }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }
    var showAlipayChoiceDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showKeywordsDialog by remember { mutableStateOf(false) }
    var isActivated by remember { mutableStateOf(ModuleUtils.isModuleEnabled()) }
    var pendingSavedToast by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!SPUtils.isPrivacyPolicyAccepted(context)) {
            showPrivacyPolicyDialog = true
        }
    }

    LaunchedEffect(Unit) {
        autoInputDelay = AppPreferencesDataStore.getString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT)
        retentionTime = AppPreferencesDataStore.getString(context, PrefConst.KEY_NOTIFICATION_RETENTION_TIME, PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT)
        smsCodeKeywords = AppPreferencesDataStore.getString(context, PrefConst.KEY_SMSCODE_KEYWORDS, PrefConst.SMSCODE_KEYWORDS_DEFAULT)
        settingsViewModel.setInternalFilesWritable()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            isActivated = ModuleUtils.isModuleEnabled() || ModuleActivationStore.isActivatedRecently(context)
            delay(1000L)
            isActivated = ModuleUtils.isModuleEnabled() || ModuleActivationStore.isActivatedRecently(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && pendingSavedToast) {
                Toast.makeText(context, context.getString(R.string.pref_sync_toast), Toast.LENGTH_SHORT).show()
                pendingSavedToast = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val markPrefsSaved = { pendingSavedToast = true }

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
                        snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Long)
                    }
                    is SettingsEvent.ShowPrivacyPolicy -> {
                        showPrivacyPolicyDialog = true
                    }
                    is SettingsEvent.ShowAlipayPacket -> {
                        showDonateDialog = true
                    }
                    is SettingsEvent.CheckUpdateError -> {
                        snackbarHostState.showSnackbar(context.getString(R.string.check_update_failed))
                    }
                    is SettingsEvent.AppAlreadyNewest -> {
                        snackbarHostState.showSnackbar(context.getString(R.string.app_already_newest))
                    }
                    else -> Unit
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(text = stringResource(id = R.string.pref_general_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_enable_title),
                summary = stringResource(id = R.string.pref_enable_summary),
                key = PrefConst.KEY_ENABLE,
                defaultValue = true,
                onSaved = markPrefsSaved
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_hide_launcher_icon_title),
                summary = stringResource(id = R.string.pref_hide_launcher_icon_summary),
                key = PrefConst.KEY_HIDE_LAUNCHER_ICON,
                defaultValue = false,
                onToggle = { enabled -> settingsViewModel.hideOrShowLauncherIcon(enabled) },
                onSaved = markPrefsSaved
            )
            Item(
                title = stringResource(id = R.string.pref_choose_theme_title),
                summary = stringResource(id = R.string.pref_choose_theme_summary)
            ) { showThemeDialog = true }
            
            var showLanguageDialog by remember { mutableStateOf(false) }
            Item(
                title = stringResource(id = R.string.pref_language_title),
                summary = stringResource(id = R.string.pref_language_summary)
            ) { showLanguageDialog = true }
 
            if (showLanguageDialog) {
                LanguageChooserDialog(
                    onDismiss = { showLanguageDialog = false },
                    onLanguageSelected = { tag ->
                        val locales = if (tag.isEmpty()) {
                            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                        } else {
                            androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                        }
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                        showLanguageDialog = false
                    }
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(text = stringResource(id = R.string.pref_sms_code_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_show_toast_title),
                summary = stringResource(id = R.string.pref_show_toast_summary),
                key = PrefConst.KEY_SHOW_TOAST,
                defaultValue = true,
                onSaved = markPrefsSaved
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_copy_to_clipboard_title),
                summary = stringResource(id = R.string.pref_copy_to_clipboard_summary),
                key = PrefConst.KEY_COPY_TO_CLIPBOARD,
                defaultValue = false,
                onSaved = markPrefsSaved
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_block_sms_title),
                summary = stringResource(id = R.string.pref_block_sms_summary),
                key = PrefConst.KEY_BLOCK_SMS,
                defaultValue = false,
                onSaved = markPrefsSaved
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_deduplicate_sms_title),
                summary = stringResource(id = R.string.pref_deduplicate_sms_summary),
                key = PrefConst.KEY_DEDUPLICATE_SMS,
                defaultValue = false,
                onSaved = markPrefsSaved
            )
            Item(
                title = stringResource(id = R.string.pref_smscode_keywords_title),
                summary = stringResource(id = R.string.pref_smscode_keywords_summary)
            ) { showKeywordsDialog = true }
            Item(
                title = stringResource(id = R.string.pref_smscode_test_title),
                summary = stringResource(id = R.string.pref_smscode_test_summary)
            ) { showSmsTestDialog = true }
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
                stateOverride = autoInputEnabled,
                onSaved = markPrefsSaved
            )
            Item(
                title = stringResource(id = R.string.pref_auto_input_code_delay_title),
                summary = stringResource(id = R.string.pref_auto_input_code_delay_summary, autoInputDelay)
            ) { showAutoInputDialog = true }
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
                defaultValue = true,
                onSaved = markPrefsSaved
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_auto_cancel_notification_title),
                summary = stringResource(id = R.string.pref_auto_cancel_notification_summary),
                key = PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                defaultValue = false,
                onSaved = markPrefsSaved
            )
            Item(
                title = stringResource(id = R.string.pref_notification_retention_time_title),
                summary = run {
                    val entries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
                    val values = stringArrayResource(id = R.array.notification_retention_time_list)
                    val index = values.indexOf(retentionTime)
                    if (index >= 0) entries[index] else retentionTime
                }
            ) { showRetentionDialog = true }
             Item(
                title = stringResource(id = R.string.action_home_faq_title),
                summary = ""
            ) { showFaqDialog = true }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(text = stringResource(id = R.string.pref_code_records_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_enable_code_records_title),
                summary = "",
                key = PrefConst.KEY_ENABLE_CODE_RECORDS,
                defaultValue = true,
                onSaved = markPrefsSaved
            )
            val recordCount by settingsViewModel.smsRecordCount.collectAsStateWithLifecycle()
            Item(
                title = stringResource(id = R.string.pref_entry_code_records_title),
                summary = stringResource(id = R.string.pref_entry_code_records_summary, recordCount.toInt())
            ) { onNavigateToRecords() }
            
            var historyLimit by remember { mutableStateOf("0") }
            var showHistoryLimitDialog by remember { mutableStateOf(false) }
            var showHistoryLimitInput by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                 historyLimit = AppPreferencesDataStore.getString(context, PrefConst.KEY_HISTORY_LIMIT, "0")
            }

            Item(
                title = stringResource(id = R.string.pref_history_limit_title),
                summary = run {
                    val entries = stringArrayResource(id = R.array.history_limit_entry_list)
                    val values = stringArrayResource(id = R.array.history_limit_value_list)
                    val index = values.indexOf(historyLimit)
                    if (index >= 0) entries[index] else "$historyLimit ${stringResource(R.string.smscode_records)}" 
                }
            ) { showHistoryLimitDialog = true }

            if (showHistoryLimitDialog) {
                RetentionDialog(
                    selectedValue = historyLimit,
                    onDismiss = { showHistoryLimitDialog = false },
                    titleId = R.string.pref_history_limit_title,
                    entriesId = R.array.history_limit_entry_list,
                    valuesId = R.array.history_limit_value_list
                ) { value ->
                    if (value == "-1") {
                        showHistoryLimitInput = true
                    } else {
                        historyLimit = value
                        scope.launch {
                            AppPreferencesDataStore.setString(context, PrefConst.KEY_HISTORY_LIMIT, value)
                            AppPreferencesDataStore.syncToSharedPrefs(context)
                            pendingSavedToast = true
                        }
                    }
                    showHistoryLimitDialog = false
                }
            }
            
            if (showHistoryLimitInput) {
                TextInputDialog(
                    title = stringResource(id = R.string.history_limit_custom_entry),
                    initialValue = if (historyLimit == "0" || historyLimit == "-1") "" else historyLimit,
                    onDismiss = { showHistoryLimitInput = false }
                ) { value ->
                   if (value.all { it.isDigit() } && value.isNotEmpty()) {
                       historyLimit = value
                       scope.launch {
                           AppPreferencesDataStore.setString(context, PrefConst.KEY_HISTORY_LIMIT, value)
                           AppPreferencesDataStore.syncToSharedPrefs(context)
                           pendingSavedToast = true
                       }
                   }
                   showHistoryLimitInput = false
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(text = stringResource(id = R.string.pref_others_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_verbose_log_mode_title),
                summary = stringResource(id = R.string.pref_verbose_log_mode_summary),
                key = PrefConst.KEY_VERBOSE_LOG_MODE,
                defaultValue = false,
                onToggle = { on -> XLog.setLogLevel(if (on) Log.VERBOSE else BuildConfig.LOG_LEVEL) },
                onSaved = markPrefsSaved
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader(text = stringResource(id = R.string.pref_about_title))
            Item(
                title = stringResource(id = R.string.pref_version_title),
                summary = stringResource(id = R.string.pref_version_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            ) { settingsViewModel.checkUpdate() }
            Item(
                title = stringResource(id = R.string.pref_join_qq_group_title),
                summary = stringResource(id = R.string.pref_join_qq_group_summary)
            ) { PackageUtils.joinQQGroup(context) }
            Item(
                title = stringResource(id = R.string.pref_join_telegram_group_title),
                summary = stringResource(id = R.string.pref_join_telegram_group_summary)
            ) { Utils.showWebPage(context, Const.TELEGRAM_GROUP_URL) }
            Item(
                title = stringResource(id = R.string.pref_source_code_title),
                summary = stringResource(id = R.string.pref_source_code_summary)
            ) { Utils.showWebPage(context, Const.PROJECT_SOURCE_CODE_URL) }
            Item(
                title = stringResource(id = R.string.pref_donate_by_alipay_title),
                summary = stringResource(id = R.string.dialog_donate_content)
            ) { showDonateDialog = true }
            Item(
                title = stringResource(id = R.string.pref_privacy_policy_title),
                summary = ""
            ) { Utils.showWebPage(context, Const.PRIVACY_POLICY_URL) }

            Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + 8.dp))
        }
    }

    if (showAutoInputDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_delay_title),
            initialValue = autoInputDelay,
            onDismiss = { showAutoInputDialog = false }
        ) { value ->
            autoInputDelay = value
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
                pendingSavedToast = true
            }
            showAutoInputDialog = false
        }
    }

    if (showRetentionDialog) {
        RetentionDialog(
            selectedValue = retentionTime,
            onDismiss = { showRetentionDialog = false }
        ) { value ->
            retentionTime = value
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_NOTIFICATION_RETENTION_TIME, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
                pendingSavedToast = true
            }
            showRetentionDialog = false
        }
    }

    if (showSmsTestDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_smscode_test_title),
            initialValue = smsTestInput,
            onDismiss = { showSmsTestDialog = false },
            singleLine = false,
            maxLines = 8
        ) { value ->
            smsTestInput = value
            settingsViewModel.performSmsCodeTest(value)
            showSmsTestDialog = false
        }
    }

    if (showKeywordsDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_smscode_keywords_title),
            initialValue = smsCodeKeywords,
            onDismiss = { showKeywordsDialog = false },
            singleLine = false,
            maxLines = 10
        ) { value ->
            val updated = if (value.isBlank()) PrefConst.SMSCODE_KEYWORDS_DEFAULT else value
            smsCodeKeywords = updated
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_SMSCODE_KEYWORDS, updated)
                AppPreferencesDataStore.syncToSharedPrefs(context)
                pendingSavedToast = true
            }
            showKeywordsDialog = false
        }
    }

    if (showFaqDialog) {
        FaqDialog(onDismiss = { showFaqDialog = false })
    }

    updateVersion?.let { version ->
        UpdateDialog(
            version = version,
            onDismiss = { settingsViewModel.clearUpdateVersion() },
            onUpdateCoolApk = { settingsViewModel.updateFromCoolApk() },
            onUpdateGithub = { settingsViewModel.updateFromGithub() }
        )
    }

    if (showThemeDialog) {
        ThemeChooserDialog(
            currentMode = themeMode,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { mode, x, y ->
                settingsViewModel.setThemeMode(mode, x, y)
                showThemeDialog = false
            }
        )
    }

    if (showDonateDialog) {
         DonateDialog(
             onDismiss = { showDonateDialog = false },
             onAlipay = { showDonateDialog = false; showAlipayChoiceDialog = true },
             onWechat = {
                 showDonateDialog = false
                 showQRCodeDialog = Pair(R.drawable.wx, "wechat")
             }
         )
    }

    if (showAlipayChoiceDialog) {
        AlipayChoiceDialog(
            onDismiss = { showAlipayChoiceDialog = false },
            onQRCode = {
                showAlipayChoiceDialog = false
                showQRCodeDialog = Pair(R.drawable.alipay, "alipay")
            },
            onToken = {
                showAlipayChoiceDialog = false
                PackageUtils.copyAlipayPocketToken(context)
                PackageUtils.startAlipayActivity(context)
            }
        )
    }

    showQRCodeDialog?.let { pair ->
        QRCodeDialog(
            resId = pair.first,
            type = pair.second,
            onDismiss = { showQRCodeDialog = null },
            onSave = { Utils.saveImageToGallery(context, pair.first, "${pair.second}_qrcode") }
        )
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyPolicyDialog = false },
            onConfirm = {
                scope.launch { SPUtils.setPrivacyPolicyAccepted(context, true) }
                showPrivacyPolicyDialog = false
            },
            onCancel = {
                scope.launch { SPUtils.setPrivacyPolicyAccepted(context, false) }
                showPrivacyPolicyDialog = false
                onExit()
            },
            onViewPolicy = {
                Utils.showWebPage(context, Const.PRIVACY_POLICY_URL)
            }
        )
    }
}

// Helper Composables (extracted and made standalone)

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun Item(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (summary.isNotEmpty()) {
            { Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SwitchItem(
    title: String,
    summary: String,
    key: String,
    defaultValue: Boolean,
    modifier: Modifier = Modifier,
    stateOverride: MutableState<Boolean>? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    onSaved: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checkedState = stateOverride ?: rememberPrefBoolean(key, defaultValue)

    fun toggle(checked: Boolean) {
        checkedState.value = checked
        scope.launch {
            AppPreferencesDataStore.setBoolean(context, key, checked)
            AppPreferencesDataStore.syncToSharedPrefs(context)
            onSaved?.invoke()
        }
        onToggle?.invoke(checked)
    }

    ListItem(
        headlineContent = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (summary.isNotEmpty()) {
            { Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        trailingContent = {
            Switch(checked = checkedState.value, onCheckedChange = { toggle(it) })
        },
        modifier = modifier.clickable { toggle(!checkedState.value) }
    )
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
    initialValue: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                maxLines = maxLines
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
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
    modifier: Modifier = Modifier,
    titleId: Int = R.string.pref_notification_retention_time_title,
    entriesId: Int = R.array.notification_retention_time_entry_list,
    valuesId: Int = R.array.notification_retention_time_list,
    onConfirm: (String) -> Unit
) {
    val entries = stringArrayResource(id = entriesId)
    val values = stringArrayResource(id = valuesId)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(id = titleId)) },
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
            TextButton(onClick = onUpdateCoolApk) { Text(stringResource(id = R.string.source_coolapk)) }
            TextButton(onClick = onUpdateGithub) { Text(stringResource(id = R.string.source_github)) }
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
    onThemeSelected: (Int, Float, Float) -> Unit
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
                   var rowCoords: LayoutCoordinates? by remember { mutableStateOf(null) }
                   val view = LocalView.current
                   
                   Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .onGloballyPositioned { rowCoords = it }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        val locationOnScreen = IntArray(2)
                                        view.getLocationOnScreen(locationOnScreen)
                                        
                                        val rootCoords = rowCoords?.positionInRoot() ?: androidx.compose.ui.geometry.Offset.Zero
                                        
                                        // Dialog Window Offset + Item Offset in Dialog + Tap Offset
                                        val finalX = locationOnScreen[0] + rootCoords.x + tapOffset.x
                                        val finalY = locationOnScreen[1] + rootCoords.y + tapOffset.y
                                        
                                        onThemeSelected(mode, finalX, finalY)
                                    }
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == currentMode, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun LanguageChooserDialog(
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) "" else currentLocales.get(0)?.toLanguageTag() ?: ""

    val languages = listOf(
        stringResource(id = R.string.language_follow_system) to "",
        stringResource(id = R.string.language_en) to "en",
        stringResource(id = R.string.language_zh_cn) to "zh-CN",
        stringResource(id = R.string.language_zh_tw) to "zh-TW"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.pref_language_title)) },
        text = {
            Column {
                languages.forEach { (label, tag) ->
                    val selected = if (tag.isEmpty()) currentTag.isEmpty() else currentTag.startsWith(tag)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(tag) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onLanguageSelected(tag) }
                        )
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
                    contentDescription = if (type == "alipay") stringResource(id = R.string.dialog_donate_alipay) else stringResource(id = R.string.dialog_donate_wechat),
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
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onViewPolicy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.privacy_dialog_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.privacy_dialog_content))
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onViewPolicy,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(id = R.string.privacy_policy_button))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.privacy_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = R.string.privacy_dialog_cancel))
            }
        }
    )
}

@Composable
fun FaqDialog(onDismiss: () -> Unit) {
    val questions = stringArrayResource(id = R.array.question_list)
    val answers = stringArrayResource(id = R.array.answer_list)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.action_home_faq_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                questions.forEachIndexed { index, question ->
                    if (question != "empty" && index < answers.size && answers[index] != "empty") {
                        Text(
                            text = "Q: $question",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = "A: ${answers[index]}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
                                hyphens = androidx.compose.ui.text.style.Hyphens.Auto
                            ),
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.okay)) }
        }
    )
}

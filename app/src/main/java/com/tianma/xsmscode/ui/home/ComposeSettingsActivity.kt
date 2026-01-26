package com.tianma.xsmscode.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.common.utils.PackageUtils
import com.tianma.xsmscode.common.utils.SPUtils
import com.tianma.xsmscode.common.utils.Utils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.ApkVersion
import com.tianma.xsmscode.data.http.NetworkError
import com.tianma.xsmscode.ui.block.AppBlockActivity
import com.tianma.xsmscode.ui.common.toUiMessage
import com.tianma.xsmscode.ui.record.CodeRecordActivity
import com.tianma.xsmscode.ui.rule.CodeRulesActivity
import kotlinx.coroutines.launch

class ComposeSettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupObservers()
        viewModel.handleArguments(intent.extras)
        getExternalFilesDir("")
        checkNotificationPermission()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsContent()
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.checkUpdateErrorEvent.observe(this) { showCheckError(it) }
        viewModel.showUpdateDialogEvent.observe(this) { showUpdateDialog(it) }
        viewModel.appAlreadyNewestEvent.observe(this) { showAppAlreadyNewest() }
        viewModel.smsCodeTestResultEvent.observe(this) { showSmsCodeTestResult(it) }
        viewModel.showPrivacyPolicyEvent.observe(this) { showPrivacyPolicyDialog() }
        viewModel.showAlipayPacketEvent.observe(this) { donateByAlipay() }
    }

    private fun showAppAlreadyNewest() {
        Toast.makeText(this, R.string.app_already_newest, Toast.LENGTH_SHORT).show()
    }

    private fun showCheckError(error: NetworkError) {
        val message = error.toUiMessage()
        val base = getString(R.string.check_update_failed)
        val detail = getString(message.resId, *message.args)
        Toast.makeText(this, "$base $detail", Toast.LENGTH_SHORT).show()
    }

    private fun showSmsCodeTestResult(code: String) {
        val text = if (TextUtils.isEmpty(code)) {
            getString(R.string.cannot_parse_smscode)
        } else {
            getString(R.string.current_sms_code, code)
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun showUpdateDialog(latestVersion: ApkVersion) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.new_version_found)
            .setMessage(latestVersion.versionInfo ?: "")
            .setPositiveButton(R.string.update_from_coolapk) { _, _ -> viewModel.updateFromCoolApk() }
            .setNegativeButton(R.string.update_from_github) { _, _ -> viewModel.updateFromGithub() }
            .show()
    }

    private fun checkNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsContent() {
        val scope = rememberCoroutineScope()
        val autoInputDelayState = remember {
            mutableStateOf(
                PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT
            )
        }
        val retentionTimeState = remember {
            mutableStateOf(
                PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT
            )
        }
        val showAutoInputDialog = remember { mutableStateOf(false) }
        val showRetentionDialog = remember { mutableStateOf(false) }
        val showSmsTestDialog = remember { mutableStateOf(false) }
        val smsTestInput = remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            autoInputDelayState.value = AppPreferencesDataStore.getString(
                this@ComposeSettingsActivity,
                PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
                PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT
            )
            retentionTimeState.value = AppPreferencesDataStore.getString(
                this@ComposeSettingsActivity,
                PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors()
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    onToggle = { enabled -> viewModel.hideOrShowLauncherIcon(enabled) }
                )
                Item(
                    title = stringResource(id = R.string.pref_choose_theme_title),
                    summary = stringResource(id = R.string.pref_choose_theme_summary)
                ) { showThemeChooser() }

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
                    title = stringResource(id = R.string.pref_smscode_test_title),
                    summary = stringResource(id = R.string.pref_smscode_test_summary)
                ) { showSmsTestDialog.value = true }
                Item(
                    title = stringResource(id = R.string.pref_code_rules_title),
                    summary = stringResource(id = R.string.pref_code_rules_summary)
                ) { CodeRulesActivity.startToMe(this@ComposeSettingsActivity) }

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
                    summary = stringResource(
                        id = R.string.pref_auto_input_code_delay_summary,
                        autoInputDelayState.value
                    )
                ) { showAutoInputDialog.value = true }
                Item(
                    title = stringResource(id = R.string.app_block_settings),
                    summary = stringResource(id = R.string.app_block_summary)
                ) { AppBlockActivity.startMe(this@ComposeSettingsActivity) }

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
                    summary = stringResource(
                        id = R.string.pref_notification_retention_time_summary,
                        retentionTimeState.value
                    )
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
                ) { CodeRecordActivity.startToMe(this@ComposeSettingsActivity) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionHeader(text = stringResource(id = R.string.pref_others_title))
                SwitchItem(
                    title = stringResource(id = R.string.pref_verbose_log_mode_title),
                    summary = stringResource(id = R.string.pref_verbose_log_mode_summary),
                    key = PrefConst.KEY_VERBOSE_LOG_MODE,
                    defaultValue = false,
                    onToggle = { on ->
                        XLog.setLogLevel(if (on) android.util.Log.VERBOSE else BuildConfig.LOG_LEVEL)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionHeader(text = stringResource(id = R.string.pref_about_title))
                Item(
                    title = stringResource(id = R.string.pref_version_title),
                    summary = stringResource(id = R.string.pref_version_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                ) { viewModel.checkUpdate() }
                Item(
                    title = stringResource(id = R.string.pref_join_qq_group_title),
                    summary = stringResource(id = R.string.pref_join_qq_group_summary, Const.QQ_GROUP_URL)
                ) { PackageUtils.joinQQGroup(this@ComposeSettingsActivity) }
                Item(
                    title = stringResource(id = R.string.pref_source_code_title),
                    summary = stringResource(id = R.string.pref_source_code_summary)
                ) { Utils.showWebPage(this@ComposeSettingsActivity, Const.PROJECT_SOURCE_CODE_URL) }
                Item(
                    title = stringResource(id = R.string.pref_donate_by_alipay_title),
                    summary = stringResource(id = R.string.dialog_donate_content)
                ) { donateByAlipay() }
                Item(
                    title = stringResource(id = R.string.pref_privacy_policy_title),
                    summary = ""
                ) { showPrivacyPolicyDialog() }
            }
        }

        if (showAutoInputDialog.value) {
            TextInputDialog(
                title = stringResource(id = R.string.pref_auto_input_code_delay_title),
                value = autoInputDelayState,
                onDismiss = { showAutoInputDialog.value = false }
            ) { value ->
                autoInputDelayState.value = value
                scope.launch {
                    AppPreferencesDataStore.setString(
                        this@ComposeSettingsActivity,
                        PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
                        value
                    )
                }
                showAutoInputDialog.value = false
            }
        }

        if (showRetentionDialog.value) {
            RetentionDialog(
                selectedValue = retentionTimeState.value,
                onDismiss = { showRetentionDialog.value = false }
            ) { value ->
                retentionTimeState.value = value
                scope.launch {
                    AppPreferencesDataStore.setString(
                        this@ComposeSettingsActivity,
                        PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                        value
                    )
                }
                showRetentionDialog.value = false
            }
        }

        if (showSmsTestDialog.value) {
            TextInputDialog(
                title = stringResource(id = R.string.pref_smscode_test_title),
                value = smsTestInput,
                onDismiss = { showSmsTestDialog.value = false }
            ) { value ->
                smsTestInput.value = value
                viewModel.performSmsCodeTest(value)
                showSmsTestDialog.value = false
            }
        }
    }

    @Composable
    private fun SectionHeader(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
    }

    @Composable
    private fun Item(title: String, summary: String, onClick: () -> Unit) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { if (summary.isNotEmpty()) Text(summary) },
            modifier = Modifier.clickable { onClick() }
        )
    }

    @Composable
    private fun SwitchItem(
        title: String,
        summary: String,
        key: String,
        defaultValue: Boolean,
        stateOverride: MutableState<Boolean>? = null,
        onToggle: ((Boolean) -> Unit)? = null
    ) {
        val scope = rememberCoroutineScope()
        val state = stateOverride ?: rememberPrefBoolean(key, defaultValue)
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            trailingContent = {
                Switch(
                    checked = state.value,
                    onCheckedChange = { checked ->
                        state.value = checked
                        scope.launch {
                            AppPreferencesDataStore.setBoolean(this@ComposeSettingsActivity, key, checked)
                        }
                        onToggle?.invoke(checked)
                    }
                )
            }
        )
    }

    @Composable
    private fun rememberPrefBoolean(key: String, defaultValue: Boolean): MutableState<Boolean> {
        val state = remember { mutableStateOf(defaultValue) }
        LaunchedEffect(key) {
            state.value = AppPreferencesDataStore.getBoolean(this@ComposeSettingsActivity, key, defaultValue)
        }
        return state
    }

    private fun showThemeChooser() {
        val currentMode = SPUtils.getThemeMode(this)
        val items = arrayOf(
            getString(R.string.theme_follow_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.pref_choose_theme_title)
            .setSingleChoiceItems(items, currentMode) { dialog, which ->
                SPUtils.setThemeMode(this, which)
                applyTheme(which)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyTheme(mode: Int) {
        val nightMode = when (mode) {
            1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    @Composable
    private fun TextInputDialog(
        title: String,
        value: MutableState<String>,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = value.value,
                    onValueChange = { value.value = it }
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(value.value) }) {
                    Text(text = stringResource(id = R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    @Composable
    private fun RetentionDialog(
        selectedValue: String,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit
    ) {
        val selected = remember { mutableStateOf(selectedValue) }
        val options = listOf(
            Pair(stringResource(id = R.string.notification_retention_time_5_secs_entry), "5"),
            Pair(stringResource(id = R.string.notification_retention_time_10_secs_entry), "10"),
            Pair(stringResource(id = R.string.notification_retention_time_30_secs_entry), "30"),
            Pair(stringResource(id = R.string.notification_retention_time_1_min_entry), "60"),
            Pair(stringResource(id = R.string.notification_retention_time_5_mins_entry), "300")
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(id = R.string.pref_notification_retention_time_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .clickable { selected.value = option.second }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            RadioButton(
                                selected = selected.value == option.second,
                                onClick = { selected.value = option.second }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option.first)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(selected.value) }) {
                    Text(text = stringResource(id = R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    private fun donateByAlipay() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_donate_title)
            .setMessage(R.string.dialog_donate_content)
            .setPositiveButton(R.string.dialog_donate_alipay) { _, _ -> showAlipayChoiceDialog() }
            .setNeutralButton(R.string.dialog_donate_wechat) { _, _ -> showQRCodeDialog(R.drawable.wx, "wechat") }
            .setNegativeButton(R.string.dialog_donate_cancel, null)
            .show()
    }

    private fun showAlipayChoiceDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_donate_alipay_choice_title)
            .setMessage(R.string.dialog_donate_alipay_choice_content)
            .setPositiveButton(R.string.dialog_donate_alipay_qrcode) { _, _ -> showQRCodeDialog(R.drawable.alipay, "alipay") }
            .setNeutralButton(R.string.dialog_donate_alipay_token) { _, _ -> copyAlipayPocketToken() }
            .show()
    }

    private fun showQRCodeDialog(resId: Int, type: String) {
        val imageView = android.widget.ImageView(this).apply {
            setImageResource(resId)
            setPadding(60, 40, 60, 20)
            adjustViewBounds = true
            setOnLongClickListener {
                saveImageToGallery(resId, "${type}_qrcode")
                true
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (type == "alipay") R.string.dialog_donate_alipay else R.string.dialog_donate_wechat)
            .setMessage(R.string.long_press_save_hint)
            .setView(imageView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (type == "alipay") {
                    PackageUtils.startAlipayActivity(this)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveImageToGallery(resId: Int, fileName: String) {
        val bitmap = android.graphics.BitmapFactory.decodeResource(resources, resId)
        val resolver = contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                Toast.makeText(this, R.string.save_to_gallery_success, Toast.LENGTH_SHORT).show()
                if (fileName.contains("alipay")) {
                    PackageUtils.startAlipayActivity(this)
                }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.save_to_gallery_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.save_to_gallery_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyAlipayPocketToken() {
        Utils.copyToClipboard(this, Const.ALIPAY_POCKET_TOKEN)
        val text = getString(R.string.alipay_red_packet_code_copied, Const.ALIPAY_POCKET_TOKEN)
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun showPrivacyPolicyDialog() {
        val text = HtmlCompat.fromHtml(
            getString(R.string.privacy_dialog_content),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.privacy_dialog_title)
            .setMessage(text)
            .setPositiveButton(R.string.privacy_dialog_confirm) { _, _ ->
                SPUtils.setPrivacyPolicyAccepted(this, true)
            }
            .setCancelable(false)
            .setNegativeButton(R.string.privacy_dialog_cancel) { _, _ ->
                SPUtils.setPrivacyPolicyAccepted(this, false)
                finish()
            }
            .show()
    }
}

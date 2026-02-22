package com.tianma.xsmscode.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterceptScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    refreshTrigger: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var smsBlacklistNumbers by remember { mutableStateOf("") }
    var smsBlacklistPrefixes by remember { mutableStateOf("") }
    var smsBlacklistRegex by remember { mutableStateOf("") }
    var smsBlacklistContent by remember { mutableStateOf("") }
    var showSmsBlacklistNumbersDialog by remember { mutableStateOf(false) }
    var showSmsBlacklistPrefixesDialog by remember { mutableStateOf(false) }
    var showSmsBlacklistRegexDialog by remember { mutableStateOf(false) }
    var showSmsBlacklistContentDialog by remember { mutableStateOf(false) }

    suspend fun reload() {
        smsBlacklistNumbers = AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, "")
        smsBlacklistPrefixes = AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, "")
        smsBlacklistRegex = AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_REGEX, "")
        smsBlacklistContent = AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_CONTENT, "")
    }

    LaunchedEffect(Unit) {
        reload()
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            reload()
        }
    }

    val notSetText = context.getString(R.string.blacklist_not_set)
    val formatSummary: (String) -> String = { raw ->
        val count = raw.split('\n', ',', ';').map { it.trim() }.count { it.isNotEmpty() }
        if (count == 0) notSetText else context.getString(R.string.blacklist_rule_count, count)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_intercept)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier.hazeEffect(hazeState, hazeStyle) { forceInvalidateOnPreDraw = true },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp),
            ) {
                SectionHeader(
                    text = stringResource(R.string.pref_sms_blacklist_title),
                    modifier = Modifier.padding(top = Const.SPACING_SMALL.dp),
                )
                SwitchItem(
                    title = stringResource(R.string.pref_enable_sms_blacklist_title),
                    summary = stringResource(R.string.pref_enable_sms_blacklist_summary),
                    key = PrefConst.KEY_ENABLE_SMS_BLACKLIST,
                    defaultValue = false,
                )
                SwitchItem(
                    title = stringResource(R.string.pref_sms_blacklist_action_delete_title),
                    summary = stringResource(R.string.pref_sms_blacklist_action_delete_summary),
                    key = PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
                    defaultValue = true,
                )
                SwitchItem(
                    title = stringResource(R.string.pref_sms_blacklist_action_block_title),
                    summary = stringResource(R.string.pref_sms_blacklist_action_block_summary),
                    key = PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
                    defaultValue = false,
                )
                Item(
                    title = stringResource(R.string.pref_sms_blacklist_numbers_title),
                    summary = formatSummary(smsBlacklistNumbers),
                ) { showSmsBlacklistNumbersDialog = true }
                Item(
                    title = stringResource(R.string.pref_sms_blacklist_prefixes_title),
                    summary = formatSummary(smsBlacklistPrefixes),
                ) { showSmsBlacklistPrefixesDialog = true }
                Item(
                    title = stringResource(R.string.pref_sms_blacklist_regex_title),
                    summary = formatSummary(smsBlacklistRegex),
                ) { showSmsBlacklistRegexDialog = true }
                Item(
                    title = stringResource(R.string.pref_sms_blacklist_content_title),
                    summary = formatSummary(smsBlacklistContent),
                ) { showSmsBlacklistContentDialog = true }

                HorizontalDivider(modifier = Modifier.padding(vertical = Const.SPACING_SMALL.dp))
            }
        }
    }

    if (showSmsBlacklistNumbersDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_numbers_title),
            initialValue = smsBlacklistNumbers,
            onDismiss = { showSmsBlacklistNumbersDialog = false },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            smsBlacklistNumbers = value
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
            }
            showSmsBlacklistNumbersDialog = false
        }
    }

    if (showSmsBlacklistPrefixesDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_prefixes_title),
            initialValue = smsBlacklistPrefixes,
            onDismiss = { showSmsBlacklistPrefixesDialog = false },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            smsBlacklistPrefixes = value
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
            }
            showSmsBlacklistPrefixesDialog = false
        }
    }

    if (showSmsBlacklistRegexDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_regex_title),
            initialValue = smsBlacklistRegex,
            onDismiss = { showSmsBlacklistRegexDialog = false },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            smsBlacklistRegex = value
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_SMS_BLACKLIST_REGEX, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
            }
            showSmsBlacklistRegexDialog = false
        }
    }

    if (showSmsBlacklistContentDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_sms_blacklist_content_title),
            initialValue = smsBlacklistContent,
            onDismiss = { showSmsBlacklistContentDialog = false },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            smsBlacklistContent = value
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_SMS_BLACKLIST_CONTENT, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
            }
            showSmsBlacklistContentDialog = false
        }
    }
}

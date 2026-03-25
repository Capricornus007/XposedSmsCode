package com.github.magisk317.smscode.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.ui.common.DismissibleSnackbarHost
import kotlinx.coroutines.launch

@Composable
fun LiteSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedSnackbarText = context.getString(R.string.pref_sync_toast)

    fun notifySaved() {
        scope.launch {
            snackbarHostState.showSnackbar(savedSnackbarText)
        }
    }

    val showToast by AppPreferencesDataStore.getBooleanFlow(
        context,
        PrefConst.KEY_SHOW_TOAST,
        true,
    ).collectAsState(initial = true)
    val copyToClipboard by AppPreferencesDataStore.getBooleanFlow(
        context,
        PrefConst.KEY_COPY_TO_CLIPBOARD,
        true,
    ).collectAsState(initial = true)
    val autoInput by AppPreferencesDataStore.getBooleanFlow(
        context,
        PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
        true,
    ).collectAsState(initial = true)
    val autoEnter by AppPreferencesDataStore.getBooleanFlow(
        context,
        PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
        false,
    ).collectAsState(initial = false)
    val inputDelay by AppPreferencesDataStore.getStringFlow(
        context,
        PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
        PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
    ).collectAsState(initial = PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT)
    val inputInterval by AppPreferencesDataStore.getStringFlow(
        context,
        PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
        PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
    ).collectAsState(initial = PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "验证码精简版设置",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "仅保留验证码解析与自动填充相关能力。",
                style = MaterialTheme.typography.bodyMedium,
            )
            LiteSwitchItem("显示验证码提示", showToast) {
                scope.launch {
                    AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_SHOW_TOAST, it)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                    notifySaved()
                }
            }
            LiteSwitchItem("复制验证码到剪贴板", copyToClipboard) {
                scope.launch {
                    AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_COPY_TO_CLIPBOARD, it)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                    notifySaved()
                }
            }
            LiteSwitchItem("自动输入验证码", autoInput) {
                scope.launch {
                    AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, it)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                    notifySaved()
                }
            }
            LiteSwitchItem("自动提交验证码", autoEnter) {
                scope.launch {
                    AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, it)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                    notifySaved()
                }
            }
            OutlinedTextField(
                value = inputDelay,
                onValueChange = { value ->
                    if (value.all { it.isDigit() }) {
                        scope.launch {
                            AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, value)
                            AppPreferencesDataStore.syncToSharedPrefs(context)
                        }
                    }
                },
                label = { Text("自动输入延迟(毫秒)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = inputInterval,
                onValueChange = { value ->
                    if (value.all { it.isDigit() }) {
                        scope.launch {
                            AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL, value)
                            AppPreferencesDataStore.syncToSharedPrefs(context)
                        }
                    }
                },
                label = { Text("自动输入间隔(毫秒)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        DismissibleSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun LiteSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

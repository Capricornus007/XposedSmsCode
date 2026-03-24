package com.github.magisk317.smscode.ui.home

import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.common.utils.ActivationDiagnosticsSnapshot
import com.github.magisk317.smscode.common.utils.ActivationDiagnosticsStore
import com.github.magisk317.smscode.common.utils.PackageUtils
import com.github.magisk317.smscode.common.utils.Utils
import com.github.magisk317.smscode.ui.common.LocalSnackbarHostState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(hazeState: HazeState, hazeStyle: HazeStyle) {
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val settingsViewModel = if (activityOwner != null) {
        koinViewModel<SettingsViewModel>(viewModelStoreOwner = activityOwner)
    } else {
        koinViewModel()
    }
    var showDonateDialog by remember { mutableStateOf(false) }
    var showAlipayChoiceDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var statusTapCount by remember { mutableStateOf(0) }
    var statusTapStartedAtMs by remember { mutableStateOf(0L) }
    var showStatusDiagnostics by remember { mutableStateOf(false) }
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    val isEnabled = ActivationDiagnosticsStore.isModuleActivated(context)
    val runtimeConnected = ActivationDiagnosticsStore.isRuntimeConnected()

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val activationDiagnostics by produceState(
        initialValue = ActivationDiagnosticsStore.snapshot(context),
        context,
    ) {
        while (true) {
            value = ActivationDiagnosticsStore.snapshot(context)
            delay(1500L)
        }
    }
    val frameworkInfoState by produceState<Pair<String, String>?>(
        initialValue = null,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.getLsposedModuleInfo()
        }
    }
    val frameworkType = frameworkInfoState?.first ?: stringResource(id = R.string.unknown)
    val frameworkVersion = frameworkInfoState?.second ?: run {
        val lsposedVersion = PackageUtils.getPackageVersion(context, Const.LSPOSED_MANAGER_PACKAGE_NAME)
        when {
            lsposedVersion != null && lsposedVersion.first.isNotBlank() ->
                "${lsposedVersion.first} (${lsposedVersion.second})"

            PackageUtils.isPackageInstalled(context, Const.LSPOSED_MANAGER_PACKAGE_NAME) ->
                stringResource(id = R.string.unknown)

            else -> stringResource(id = R.string.not_installed)
        }
    }
    val hasRootAccessState by produceState(
        initialValue = false,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.hasRootAccess()
        }
    }
    val appVersionState by produceState<Pair<String, Long>?>(
        initialValue = null,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.getPackageVersion(context, context.packageName)
        }
    }
    val appVersionName = appVersionState?.first?.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.unknown)
    val appVersionCode = appVersionState?.second?.toString() ?: stringResource(id = R.string.unknown)

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
            state = listState,
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp + 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StatusCard(
                    isEnabled = isEnabled,
                    showDiagnostics = showStatusDiagnostics,
                    diagnostics = buildStatusDiagnostics(
                        context = context,
                        snapshot = activationDiagnostics,
                        runtimeConnected = runtimeConnected,
                    ),
                    onClick = {
                        val now = SystemClock.uptimeMillis()
                        val withinWindow = now - statusTapStartedAtMs <= 1800L
                        statusTapCount = if (withinWindow) statusTapCount + 1 else 1
                        statusTapStartedAtMs = now
                        if (statusTapCount >= 5) {
                            showStatusDiagnostics = !showStatusDiagnostics
                            statusTapCount = 0
                            statusTapStartedAtMs = 0L
                            showMessage(
                                if (showStatusDiagnostics) {
                                    context.getString(R.string.status_diag_shown)
                                } else {
                                    context.getString(R.string.status_diag_hidden)
                                },
                            )
                        }
                    },
                )
            }
            item {
                io.github.magisk317.uikit.surface.DetailSectionCard(
                    title = stringResource(id = R.string.app_name),
                    summary = stringResource(id = R.string.status_diag_service_title),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.version_name),
                            value = appVersionName,
                            leadingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Label,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.version_code),
                            value = appVersionCode,
                            leadingContent = {
                                Icon(
                                    Icons.Default.Numbers,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        val rootHint = stringResource(id = R.string.root_permission_hint)
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.framework_type),
                            value = frameworkType,
                            leadingContent = {
                                Icon(
                                    Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = if (hasRootAccessState) {
                                null
                            } else {
                                { showMessage(rootHint) }
                            },
                        )
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.framework_version),
                            value = frameworkVersion,
                            leadingContent = {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = if (hasRootAccessState) {
                                null
                            } else {
                                { showMessage(rootHint) }
                            },
                        )
                    }
                }
            }

            item {
                io.github.magisk317.uikit.surface.DetailSectionCard(
                    title = stringResource(id = R.string.android_version),
                    summary = Build.MODEL,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.android_version),
                            value = Build.VERSION.RELEASE,
                            leadingContent = {
                                Icon(
                                    Icons.Default.Android,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.android_codename),
                            value = Build.VERSION.CODENAME,
                            leadingContent = {
                                Icon(
                                    Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.api_level),
                            value = Build.VERSION.SDK_INT.toString(),
                            leadingContent = {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.manufacturer),
                            value = Build.MANUFACTURER,
                            leadingContent = {
                                Icon(
                                    Icons.Default.Business,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.model),
                            value = Build.MODEL,
                            leadingContent = {
                                Icon(
                                    Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                }
            }

            item {
                io.github.magisk317.uikit.surface.DetailSectionCard(
                    title = stringResource(id = R.string.check_update_title),
                    summary = stringResource(id = R.string.pref_source_code_summary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.check_update_title),
                            value = stringResource(id = R.string.check_update_summary),
                            leadingContent = {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                settingsViewModel.requestPreferredUpdate()
                            },
                        )
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.pref_join_qq_group_title),
                            value = stringResource(id = R.string.pref_join_qq_group_summary),
                            leadingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = { PackageUtils.joinQQGroup(context)?.let(::showMessage) },
                        )
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.pref_join_telegram_group_title),
                            value = stringResource(id = R.string.pref_join_telegram_group_summary),
                            leadingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = { Utils.showWebPage(context, Const.TELEGRAM_GROUP_URL)?.let(::showMessage) },
                        )
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.pref_source_code_title),
                            value = stringResource(id = R.string.pref_source_code_summary),
                            leadingContent = {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = { Utils.showWebPage(context, Const.PROJECT_SOURCE_CODE_URL)?.let(::showMessage) },
                        )
                        io.github.magisk317.uikit.surface.DetailRow(
                            label = stringResource(id = R.string.pref_donate_by_alipay_title),
                            value = stringResource(id = R.string.dialog_donate_summary),
                            leadingContent = {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = { showDonateDialog = true },
                        )
                    }
                }
            }
        }

        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets.statusBars,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .hazeEffect(hazeState, hazeStyle) {
                    forceInvalidateOnPreDraw = true
                },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { showDonateDialog = false },
            onAlipay = {
                showDonateDialog = false
                showAlipayChoiceDialog = true
            },
            onWechat = {
                showDonateDialog = false
                showQRCodeDialog = Pair(R.drawable.wx, "wechat")
            },
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
                showMessage(PackageUtils.copyAlipayPocketToken(context))
                PackageUtils.startAlipayActivity(context)?.let(::showMessage)
            },
        )
    }

    showQRCodeDialog?.let { pair ->
        QRCodeDialog(
            resId = pair.first,
            type = pair.second,
            onDismiss = { showQRCodeDialog = null },
            onSave = {
                Utils.saveImageToGallery(context, pair.first, "${pair.second}_qrcode")
                    .forEach(::showMessage)
            },
        )
    }
}

@Composable
fun StatusCard(
    isEnabled: Boolean,
    showDiagnostics: Boolean,
    diagnostics: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
) {
    val containerColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        onClick = { onClick?.invoke() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Column {
                    Text(
                        text = if (isEnabled) stringResource(id = R.string.status_working) else stringResource(id = R.string.status_not_active),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!isEnabled) {
                        Text(
                            text = stringResource(id = R.string.status_tip),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (showDiagnostics && diagnostics.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    diagnostics.forEach { (label, value) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.8f),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildStatusDiagnostics(
    context: android.content.Context,
    snapshot: ActivationDiagnosticsSnapshot,
    runtimeConnected: Boolean,
): List<Pair<String, String>> {
    val serviceValue = buildString {
        append(
            context.getString(
                if (runtimeConnected) {
                    R.string.status_diag_connected
                } else {
                    R.string.status_diag_disconnected
                },
            ),
        )
        if (snapshot.lastServiceBindAtMs > 0L) {
            append(" · ")
            append(context.getString(R.string.status_diag_last_service_prefix))
            append(" ")
            append(formatStatusDiagnosticTime(context, snapshot.lastServiceBindAtMs))
        }
        if (snapshot.lastServiceFrameworkName.isNotBlank() || snapshot.lastServiceFrameworkVersion.isNotBlank()) {
            append(" · ")
            append(snapshot.lastServiceFrameworkName.ifBlank { context.getString(R.string.unknown) })
            append(" ")
            append(snapshot.lastServiceFrameworkVersion.ifBlank { context.getString(R.string.unknown) })
        }
    }
    val hookProcess = listOf(
        snapshot.lastHookPackage.ifBlank { context.getString(R.string.status_diag_none) },
        snapshot.lastHookProcess.ifBlank { context.getString(R.string.status_diag_none) },
    ).joinToString(" / ")
    val hookTime = buildString {
        append(formatStatusDiagnosticTime(context, snapshot.lastHookAtMs))
        if (snapshot.lastHookSource.isNotBlank()) {
            append(" · ")
            append(snapshot.lastHookSource)
        }
    }
    return listOf(
        context.getString(R.string.status_diag_service_title) to serviceValue,
        context.getString(R.string.status_diag_hook_process_title) to hookProcess,
        context.getString(R.string.status_diag_hook_time_title) to hookTime,
    )
}

private fun formatStatusDiagnosticTime(context: android.content.Context, timestampMs: Long): String {
    if (timestampMs <= 0L) return context.getString(R.string.status_diag_none)
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
}

@Composable
fun InfoItem(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

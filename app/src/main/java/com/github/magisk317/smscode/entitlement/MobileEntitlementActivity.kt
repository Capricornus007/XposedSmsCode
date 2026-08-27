package com.github.magisk317.smscode.entitlement

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.ui.theme.AppTheme
import io.github.magisk317.uikit.theme.UiKitStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileEntitlementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        XLog.w("Mobile entitlement activity created")
        setContent {
            AppTheme(themeMode = 0, uiKitStyle = UiKitStyle.Expressive.value) {
                MobileEntitlementScreen(
                    activity = this@MobileEntitlementActivity,
                    onBack = ::finish,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileEntitlementScreen(
    activity: Activity,
    onBack: () -> Unit,
) {
    val context: Context = activity
    val scope = rememberCoroutineScope()
    var evaluation by remember {
        mutableStateOf(MobileEntitlementCoordinator.readCachedEvaluation())
    }
    var busyAction by remember { mutableStateOf<ActivationAction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var licenseCodeInput by remember { mutableStateOf("") }
    var savedLicenseCode by remember { mutableStateOf<String?>(null) }

    fun refreshStatus(force: Boolean = false) {
        scope.launch {
            busyAction = ActivationAction.REFRESH
            message = null
            XLog.w("Mobile entitlement refresh started")
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.refresh(context, force = force)
                }
            }.onSuccess {
                evaluation = it
                savedLicenseCode = MobileEntitlementCoordinator.readSavedLicenseCode(context)
                XLog.w(
                    "Mobile entitlement refresh finished status=%s allowed=%s renewDue=%s",
                    it.status,
                    it.automationAllowed,
                    it.renewDue,
                )
            }.onFailure {
                XLog.e("Mobile entitlement refresh failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    fun openTelegram(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { message = it.message ?: it.javaClass.simpleName }
    }

    fun openTelegramBot() {
        scope.launch {
            busyAction = ActivationAction.TELEGRAM
            message = null
            XLog.w("Mobile entitlement Telegram activation started")
            runCatching {
                val challenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createTelegramChallenge(context)
                }
                XLog.w("Mobile entitlement Telegram challenge received id=%s", challenge.id.take(8))
                openTelegram(challenge.botUrl)
            }.onFailure {
                XLog.e("Mobile entitlement Telegram activation failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    fun activateWithCode() {
        val trimmed = licenseCodeInput.trim()
        if (trimmed.length != 32) {
            message = context.getString(R.string.mobile_entitlement_license_code_invalid)
            return
        }
        scope.launch {
            busyAction = ActivationAction.LICENSE_CODE
            message = null
            XLog.w("Mobile entitlement token activation started")
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.activateByToken(context, trimmed)
                }
            }.onSuccess {
                evaluation = it
                savedLicenseCode = MobileEntitlementCoordinator.readSavedLicenseCode(context)
                licenseCodeInput = ""
                XLog.w(
                    "Mobile entitlement token activation success status=%s allowed=%s",
                    it.status,
                    it.automationAllowed,
                )
            }.onFailure {
                XLog.e("Mobile entitlement token activation failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    LaunchedEffect(Unit) {
        savedLicenseCode = withContext(Dispatchers.IO) {
            MobileEntitlementCoordinator.readSavedLicenseCode(context)
        }
        refreshStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mobile_entitlement_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val currentEvaluation = evaluation
                    Text(
                        text = stringResource(
                            R.string.mobile_entitlement_status,
                            when (currentEvaluation?.status) {
                                MobileEntitlementStatus.UNACTIVATED ->
                                    stringResource(R.string.mobile_entitlement_status_not_activated)
                                null -> stringResource(R.string.mobile_entitlement_not_loaded)
                                else -> currentEvaluation.status.name
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.mobile_entitlement_automation,
                            if (evaluation?.automationAllowed == true) {
                                stringResource(R.string.mobile_entitlement_allowed)
                            } else {
                                stringResource(R.string.mobile_entitlement_paused)
                            },
                        ),
                    )
                    evaluation?.claims?.expiresAt?.let { expiresAt ->
                        Text(stringResource(R.string.mobile_entitlement_expires, formatEpoch(expiresAt)))
                    }
                    if (evaluation?.renewDue == true) {
                        Text(
                            text = stringResource(R.string.mobile_entitlement_renew_due),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            val isActivated = evaluation?.status == MobileEntitlementStatus.ACTIVE ||
                evaluation?.status == MobileEntitlementStatus.GRACE
            val displayCode = io.github.magisk317.uikit.text.maskSensitiveIdentifier(
                value = savedLicenseCode?.trim()?.uppercase(),
                expectedLength = 32,
                maskLength = 4,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.mobile_entitlement_license_code_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (isActivated) {
                        OutlinedTextField(
                            value = displayCode.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text(stringResource(R.string.mobile_entitlement_license_code_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            ),
                            singleLine = true,
                        )
                    } else {
                        OutlinedTextField(
                            value = licenseCodeInput,
                            onValueChange = { licenseCodeInput = it.trim().uppercase() },
                            label = { Text(stringResource(R.string.mobile_entitlement_license_code_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = busyAction == null,
                        )
                        Button(
                            onClick = ::activateWithCode,
                            enabled = busyAction == null && licenseCodeInput.trim().length == 32,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busyAction == ActivationAction.LICENSE_CODE) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.mobile_entitlement_license_code_confirm))
                        }
                        Text(
                            text = stringResource(R.string.mobile_entitlement_license_code_get_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val currentEvaluation = evaluation
            val showActivationActions = currentEvaluation == null ||
                currentEvaluation.status != MobileEntitlementStatus.ACTIVE ||
                currentEvaluation.renewDue
            if (showActivationActions) {
                OutlinedButton(
                    onClick = ::openTelegramBot,
                    enabled = busyAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busyAction == ActivationAction.TELEGRAM) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.mobile_entitlement_activate_telegram))
                }
            }
            OutlinedButton(
                onClick = { refreshStatus(force = true) },
                enabled = busyAction == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.mobile_entitlement_refresh))
            }
            message?.let {
                Text(
                    text = stringResource(R.string.mobile_entitlement_error, it),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private enum class ActivationAction {
    REFRESH,
    LICENSE_CODE,
    TELEGRAM,
}

private fun formatEpoch(epochSeconds: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))

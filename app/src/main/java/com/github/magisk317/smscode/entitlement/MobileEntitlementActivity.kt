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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.ui.theme.AppTheme
import io.github.magisk317.uikit.theme.UiKitStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class MobileEntitlementActivity : ComponentActivity() {
    private val googleSignIn: MobileEntitlementGoogleSignIn by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        XLog.w("Mobile entitlement activity created")
        setContent {
            AppTheme(themeMode = 0, uiKitStyle = UiKitStyle.Expressive.value) {
                MobileEntitlementScreen(
                    activity = this@MobileEntitlementActivity,
                    googleSignIn = googleSignIn,
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
    googleSignIn: MobileEntitlementGoogleSignIn,
    onBack: () -> Unit,
) {
    val context: Context = activity
    val scope = rememberCoroutineScope()
    var evaluation by remember { mutableStateOf<MobileEntitlementEvaluation?>(null) }
    var challenge by remember { mutableStateOf<MobileEntitlementChallenge?>(null) }
    var busyAction by remember { mutableStateOf<ActivationAction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refreshStatus() {
        scope.launch {
            busyAction = ActivationAction.REFRESH
            message = null
            XLog.w("Mobile entitlement refresh started")
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.refresh(context)
                }
            }.onSuccess {
                evaluation = it
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

    fun createChallenge() {
        scope.launch {
            busyAction = ActivationAction.TELEGRAM
            message = null
            XLog.w("Mobile entitlement Telegram activation started")
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createTelegramChallenge(context)
                }
            }.onSuccess {
                challenge = it
                XLog.w("Mobile entitlement Telegram challenge received id=%s", it.id.take(8))
                openTelegram(it.botUrl)
            }.onFailure {
                XLog.e("Mobile entitlement Telegram activation failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    fun activateWithGoogle() {
        scope.launch {
            busyAction = ActivationAction.GOOGLE
            message = null
            var googleBotUrl: String? = null
            runCatching {
                val challenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createGoogleChallenge(context)
                }
                googleBotUrl = challenge.botUrl
                val idToken = googleSignIn.getIdToken(
                    activity = activity,
                    serverClientId = BuildConfig.MOBILE_ENTITLEMENT_GOOGLE_WEB_CLIENT_ID,
                    nonce = challenge.nonce,
                )
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.activateWithGoogleIdToken(
                        context = context,
                        challengeId = challenge.id,
                        idToken = idToken,
                    )
                }
            }.onSuccess { state ->
                if (state.status == MobileEntitlementActivationStatus.APPROVED) {
                    evaluation = state.evaluation
                } else {
                    val botUrl = googleBotUrl ?: state.botUrl
                    if (botUrl.isNullOrBlank()) {
                        message = context.getString(R.string.mobile_entitlement_telegram_required)
                    } else {
                        challenge = MobileEntitlementChallenge(
                            id = state.challengeId,
                            expiresAt = state.expiresAt ?: 0L,
                            botUrl = botUrl,
                        )
                        openTelegram(botUrl)
                    }
                }
            }
                .onFailure {
                    XLog.e("Google activation failed", it)
                    message = it.message ?: it.javaClass.simpleName
                }
            busyAction = null
        }
    }

    LaunchedEffect(Unit) {
        val pendingChallengeId = withContext(Dispatchers.IO) {
            MobileEntitlementCoordinator.readPendingChallenge(context)
        }
        if (!pendingChallengeId.isNullOrBlank()) {
            challenge = MobileEntitlementChallenge(
                id = pendingChallengeId,
                expiresAt = 0L,
                botUrl = "",
            )
        }
        refreshStatus()
    }

    LaunchedEffect(challenge?.id) {
        val activeChallenge = challenge ?: return@LaunchedEffect
        while (isActive) {
            delay(3_000)
            val state = runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.pollTelegramChallenge(context, activeChallenge.id)
                }
            }.onSuccess {
                XLog.w(
                    "Mobile entitlement Telegram poll status=%s id=%s",
                    it.status,
                    activeChallenge.id.take(8),
                )
            }.onFailure {
                XLog.e(
                    "Mobile entitlement Telegram poll failed id=%s",
                    activeChallenge.id.take(8),
                    it,
                )
            }.getOrNull()
            if (state == null) {
                message = context.getString(R.string.mobile_entitlement_poll_failed)
                continue
            }
            when (state.status) {
                MobileEntitlementActivationStatus.PENDING -> Unit
                MobileEntitlementActivationStatus.APPROVED -> {
                    evaluation = state.evaluation
                    MobileEntitlementCoordinator.clearPendingChallenge(context)
                    challenge = null
                    break
                }
                MobileEntitlementActivationStatus.EXPIRED -> {
                    message = context.getString(R.string.mobile_entitlement_challenge_expired)
                    MobileEntitlementCoordinator.clearPendingChallenge(context)
                    challenge = null
                    break
                }
                MobileEntitlementActivationStatus.CLAIMED -> {
                    message = "授权请求已领取，请重新发起激活。"
                    MobileEntitlementCoordinator.clearPendingChallenge(context)
                    challenge = null
                    break
                }
            }
        }
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
            val currentEvaluation = evaluation
            val showActivationActions = currentEvaluation == null ||
                currentEvaluation.status != MobileEntitlementStatus.ACTIVE ||
                currentEvaluation.renewDue
            if (showActivationActions) {
                Button(
                    onClick = ::createChallenge,
                    enabled = busyAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busyAction == ActivationAction.TELEGRAM) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.mobile_entitlement_activate_telegram))
                }
                if (BuildConfig.MOBILE_ENTITLEMENT_CHANNEL == "play") {
                    if (BuildConfig.MOBILE_ENTITLEMENT_GOOGLE_WEB_CLIENT_ID.isBlank()) {
                        Text(
                            text = stringResource(R.string.mobile_entitlement_play_flow),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Button(
                            onClick = ::activateWithGoogle,
                            enabled = busyAction == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busyAction == ActivationAction.GOOGLE) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.mobile_entitlement_activate_google))
                        }
                    }
                }
            }
            challenge?.let { pendingChallenge ->
                Text(stringResource(R.string.mobile_entitlement_pending))
                if (pendingChallenge.botUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = { openTelegram(pendingChallenge.botUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.mobile_entitlement_open_telegram))
                    }
                }
            }
            OutlinedButton(
                onClick = ::refreshStatus,
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
    TELEGRAM,
    GOOGLE,
}

private fun formatEpoch(epochSeconds: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))

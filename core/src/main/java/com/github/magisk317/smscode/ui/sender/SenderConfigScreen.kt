package com.github.magisk317.smscode.ui.sender

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.magisk317.smscode.forwarder.utils.SenderType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.magisk317.smscode.ui.sender.forms.*

@Composable
fun SenderConfigScreen(
    senderId: Long,
    senderTypeArg: Int,
    onBack: (Boolean) -> Unit,
    viewModel: SenderViewModel = viewModel()
) {
    var type by remember { mutableStateOf(senderTypeArg) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(senderId) {
        // Reset per-screen save state to avoid carrying stale status across entries.
        viewModel.clearLastSavedStatus()
        if (senderId != 0L) {
            val sender = viewModel.getSender(senderId)
            if (sender != null) {
                type = sender.type
            }
        }
        isLoaded = true
    }

    val lastSavedStatus by viewModel.lastSavedStatus.collectAsState()

    val handleBack: () -> Unit = {
        // Only reopen type chooser when creating a brand new sender and nothing was saved.
        // If user already saved draft/status, return to sender list directly.
        val reopenTypeDialog = senderId == 0L && lastSavedStatus == null
        viewModel.clearLastSavedStatus()
        onBack(reopenTypeDialog)
    }

    if (!isLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    when (type) {
        SenderType.DINGTALK_GROUP_ROBOT -> DingtalkConfigForm(senderId, handleBack, viewModel)
        SenderType.EMAIL -> EmailConfigForm(senderId, handleBack, viewModel)
        SenderType.BARK -> BarkConfigForm(senderId, handleBack, viewModel)
        SenderType.WEBHOOK -> WebhookConfigForm(senderId, handleBack, viewModel)
        SenderType.WEWORK_ROBOT -> WeworkRobotConfigForm(senderId, handleBack, viewModel)
        SenderType.WEWORK_AGENT -> WeworkAgentConfigForm(senderId, handleBack, viewModel)
        SenderType.SERVERCHAN -> ServerchanConfigForm(senderId, handleBack, viewModel)
        SenderType.PUSHPLUS -> PushplusConfigForm(senderId, handleBack, viewModel)
        SenderType.TELEGRAM -> TelegramConfigForm(senderId, handleBack, viewModel)
        SenderType.SMS -> SmsConfigForm(senderId, handleBack, viewModel)
        SenderType.FEISHU -> FeishuConfigForm(senderId, handleBack, viewModel)
        SenderType.GOTIFY -> GotifyConfigForm(senderId, handleBack, viewModel)
        SenderType.DINGTALK_INNER_ROBOT -> DingtalkInnerConfigForm(senderId, handleBack, viewModel)
        SenderType.FEISHU_APP -> FeishuAppConfigForm(senderId, handleBack, viewModel)
        SenderType.URL_SCHEME -> UrlSchemeConfigForm(senderId, handleBack, viewModel)
        SenderType.SOCKET -> SocketConfigForm(senderId, handleBack, viewModel)
        else -> DingtalkConfigForm(senderId, handleBack, viewModel)
    }
}

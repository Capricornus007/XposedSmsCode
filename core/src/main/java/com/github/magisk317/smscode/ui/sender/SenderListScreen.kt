package com.github.magisk317.smscode.ui.sender

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.utils.SenderType
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderListScreen(
    viewModel: SenderViewModel = viewModel(),
    onAddClick: (Int) -> Unit,
    onEditClick: (Long) -> Unit,
    onRulesClick: (Long) -> Unit,
    forceShowTypeDialog: Boolean = false,
    onForceShowHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val senders by viewModel.senderList.collectAsState()
    var showTypeDialog by remember { mutableStateOf(false) }
    LaunchedEffect(forceShowTypeDialog) {
        if (forceShowTypeDialog) {
            showTypeDialog = true
            onForceShowHandled()
        }
    }

    if (showTypeDialog) {
        val supportedTypes = listOf(
            SenderType.DINGTALK_GROUP_ROBOT to "钉钉群机器人",
            SenderType.EMAIL to "邮件",
            SenderType.BARK to "Bark",
            SenderType.WEBHOOK to "Webhook",
            SenderType.WEWORK_ROBOT to "企业微信群机器人",
            SenderType.WEWORK_AGENT to "企业微信应用",
            SenderType.SERVERCHAN to "Server酱",
            SenderType.TELEGRAM to "Telegram",
            SenderType.SMS to "短信",
            SenderType.FEISHU to "飞书机器人",
            SenderType.PUSHPLUS to "PushPlus",
            SenderType.GOTIFY to "Gotify",
            SenderType.DINGTALK_INNER_ROBOT to "钉钉内部机器人",
            SenderType.FEISHU_APP to "飞书应用",
            SenderType.URL_SCHEME to "Url Scheme",
            SenderType.SOCKET to "Socket"
        )

        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.88f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showTypeDialog = false },
            title = { Text("选择新建通道类型") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(supportedTypes) { (type, name) ->
                        Button(
                            onClick = {
                                showTypeDialog = false
                                onAddClick(type)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTypeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("发送通道") }) },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp),
                onClick = { showTypeDialog = true },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Sender")
            }
        }
    ) { paddingValues ->
        if (senders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无发送通道，请点击右下角添加")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(senders, key = { it.id }) { sender ->
                    SenderCard(
                        sender = sender,
                        onEdit = { onEditClick(sender.id) },
                        onToggle = { enabled ->
                            if (enabled) {
                                val result = viewModel.validateSenderForEnable(sender)
                                if (!result.valid) {
                                    Toast.makeText(context, "无法开启：${result.message}", Toast.LENGTH_LONG).show()
                                } else {
                                    viewModel.toggleSenderStatus(sender, enabled)
                                }
                            } else {
                                viewModel.toggleSenderStatus(sender, enabled)
                            }
                        },
                        onDelete = { viewModel.deleteSender(sender) },
                        onViewRules = { onRulesClick(sender.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SenderCard(
    sender: Sender,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onViewRules: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sender.name.ifEmpty { "未命名通道" },
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = sender.status == 1,
                    onCheckedChange = { onToggle(it) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            Text(
                text = "类型: ${getSenderTypeName(sender.type)} | 修改于: ${sdf.format(sender.time)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onViewRules) {
                    Text("规则")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

fun getSenderTypeName(type: Int): String {
    return when (type) {
        SenderType.DINGTALK_GROUP_ROBOT -> "钉钉群机器人"
        SenderType.EMAIL -> "邮件"
        SenderType.BARK -> "Bark"
        SenderType.WEBHOOK -> "Webhook"
        SenderType.WEWORK_ROBOT -> "企业微信群机器人"
        SenderType.WEWORK_AGENT -> "企业微信应用"
        SenderType.SERVERCHAN -> "Server酱"
        SenderType.TELEGRAM -> "Telegram机器人"
        SenderType.SMS -> "短信"
        SenderType.FEISHU -> "飞书机器人"
        SenderType.PUSHPLUS -> "PushPlus"
        SenderType.GOTIFY -> "Gotify"
        SenderType.DINGTALK_INNER_ROBOT -> "钉钉内部机器人"
        SenderType.FEISHU_APP -> "飞书应用"
        SenderType.URL_SCHEME -> "Url Scheme"
        SenderType.SOCKET -> "Socket"
        else -> "未知通道 ($type)"
    }
}

package com.github.magisk317.smscode.ui.sender

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.magisk317.smscode.forwarder.entity.ForwardCommonConfig
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.utils.ForwardCommonConfigStore
import com.github.magisk317.smscode.forwarder.utils.SenderType
import com.tianma.xsmscode.core.BuildConfig
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TemplateVariable(
    val label: String,
    val token: String,
)

private val forwardTemplateVariables = listOf(
    TemplateVariable("来源号码", "{{FROM}}"),
    TemplateVariable("短信内容", "{{SMS}}"),
    TemplateVariable("卡槽备注", "{{CARD_SLOT}}"),
    TemplateVariable("卡槽主键", "{{CARD_SUBID}}"),
    TemplateVariable("来源姓名", "{{CONTACT_NAME}}"),
    TemplateVariable("来源归属", "{{PHONE_AREA}}"),
    TemplateVariable("APP包名", "{{PACKAGE_NAME}}"),
    TemplateVariable("APP应用名", "{{APP_NAME}}"),
    TemplateVariable("通知内容", "{{MSG}}"),
    TemplateVariable("电池电量", "{{BATTERY_PCT}}"),
    TemplateVariable("电池状态", "{{BATTERY_STATUS}}"),
    TemplateVariable("充电方式", "{{BATTERY_PLUGGED}}"),
    TemplateVariable("电池完整信息", "{{BATTERY_INFO}}"),
    TemplateVariable("电池简单信息", "{{BATTERY_INFO_SIMPLE}}"),
    TemplateVariable("公网IPv4", "{{IPV4}}"),
    TemplateVariable("公网IPv6", "{{IPV6}}"),
    TemplateVariable("IP地址列表", "{{IP_LIST}}"),
    TemplateVariable("网络状态", "{{NET_TYPE}}"),
    TemplateVariable("接收时间", "{{RECEIVE_TIME}}"),
    TemplateVariable("当前时间", "{{CURRENT_TIME}}"),
    TemplateVariable("设备名称", "{{DEVICE_NAME}}"),
    TemplateVariable("软件版本", "{{APP_VERSION}}"),
)
private val templateTokenRegex = Regex("\\{\\{[^{}]+\\}\\}")
private val cardSlotLineRegex = Regex("(?m)^(\\s*)卡槽([:：])")

private fun toAppNotifyTemplate(template: String): String {
    return template
        .replace(cardSlotLineRegex, "$1应用$2")
        .replace("【卡槽与来源】", "【应用与来源】")
}

private fun appNotifyDefaultTemplate(): String = toAppNotifyTemplate(ForwardCommonConfigStore.defaultTemplate())
private fun appNotifyFullTemplate(): String = toAppNotifyTemplate(ForwardCommonConfigStore.fullInfoTemplate())

private val appNotifyTemplateVariables = forwardTemplateVariables.map { variable ->
    when (variable.token) {
        "{{CARD_SLOT}}" -> variable.copy(label = "应用备注")
        "{{CARD_SUBID}}" -> variable.copy(label = "应用主键")
        else -> variable
    }
}
private const val DIALOG_WIDTH_FRACTION = 0.92f

private fun buildSmsPreviewMessage(): com.github.magisk317.smscode.forwarder.entity.MsgInfo {
    return com.github.magisk317.smscode.forwarder.entity.MsgInfo(
        type = "sms",
        from = "10690001234",
        content = "【测试银行】您的验证码为 123456，请勿泄露。",
        date = Date(),
        simInfo = "SIM1",
        simSlot = 0,
        subId = 1,
        contactName = "测试银行",
        phoneArea = "上海",
    )
}

private fun buildAppNotifyPreviewMessage(): com.github.magisk317.smscode.forwarder.entity.MsgInfo {
    return com.github.magisk317.smscode.forwarder.entity.MsgInfo(
        type = "app_notify",
        from = "微信支付",
        content = "收款到账 52.00 元",
        date = Date(),
        simInfo = "微信",
        packageName = "com.tencent.mm",
        appName = "微信",
        title = "微信支付",
        message = "张三向你转账 52.00 元",
        contactName = "微信支付",
    )
}

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
    val senders by viewModel.senderList.collectAsStateWithLifecycle()
    val commonConfig by viewModel.forwardCommonConfig.collectAsStateWithLifecycle()
    val appNotifyTemplate by viewModel.appNotifyTemplate.collectAsStateWithLifecycle()
    var showTypeDialog by remember { mutableStateOf(false) }
    var showCommonConfigDialog by remember { mutableStateOf(false) }
    var showAppNotifyConfigDialog by remember { mutableStateOf(false) }
    LaunchedEffect(forceShowTypeDialog) {
        if (forceShowTypeDialog) {
            showTypeDialog = true
            onForceShowHandled()
        }
    }

    if (showTypeDialog) {
        val otherChannels = mutableListOf(
            SenderType.EMAIL to "邮件",
            SenderType.URL_SCHEME to "Url Scheme",
            SenderType.SOCKET to "Socket",
        )
        if (BuildConfig.ENABLE_SMS_CHANNEL) {
            otherChannels.add(1, SenderType.SMS to "短信")
        }
        val supportedTypeGroups = listOf(
            "企业协作" to listOf(
                SenderType.DINGTALK_GROUP_ROBOT to "钉钉群机器人",
                SenderType.DINGTALK_INNER_ROBOT to "钉钉内部机器人",
                SenderType.FEISHU to "飞书机器人",
                SenderType.FEISHU_APP to "飞书应用",
                SenderType.WEWORK_ROBOT to "企微群机器人",
                SenderType.WEWORK_AGENT to "企微应用",
            ),
            "消息推送" to listOf(
                SenderType.TELEGRAM to "Telegram",
                SenderType.WEBHOOK to "Webhook",
                SenderType.SERVERCHAN to "Server酱",
                SenderType.PUSHPLUS to "PushPlus",
                SenderType.GOTIFY to "Gotify",
                SenderType.BARK to "Bark",
            ),
            "其他" to listOf(
                *otherChannels.toTypedArray(),
            ),
        )

        @Suppress("MagicNumber")
        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.88f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showTypeDialog = false },
            title = { Text("选择新建通道类型") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    supportedTypeGroups.forEach { (groupName, groupItems) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = groupName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                        groupItems.forEach { (type, name) ->
                            item {
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

    if (showCommonConfigDialog) {
        ForwardCommonConfigDialog(
            currentConfig = commonConfig,
            onDismiss = { showCommonConfigDialog = false },
            onSave = {
                viewModel.saveForwardCommonConfig(it)
                showCommonConfigDialog = false
            },
        )
    }
    if (showAppNotifyConfigDialog) {
        AppNotifyTemplateDialog(
            currentTemplate = appNotifyTemplate,
            currentCommonConfig = commonConfig,
            onDismiss = { showAppNotifyConfigDialog = false },
            onSave = {
                viewModel.saveAppNotifyTemplate(it)
                showAppNotifyConfigDialog = false
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("通道配置") }) },
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
        val listBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "forward_common_config") {
                ForwardCommonConfigCard(
                    config = commonConfig,
                    onEdit = { showCommonConfigDialog = true },
                )
            }
            item(key = "app_notify_config") {
                AppNotifyConfigCard(
                    template = appNotifyTemplate,
                    onEdit = { showAppNotifyConfigDialog = true },
                )
            }

            if (senders.isEmpty()) {
                item(key = "no_sender_hint") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("暂无发送通道，请点击右下角添加")
                    }
                }
            } else {
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
private fun ForwardCommonConfigCard(
    config: ForwardCommonConfig,
    onEdit: () -> Unit,
) {
    val isDefaultTemplate = config.messageTemplate.isBlank()
    val templatePreview = if (isDefaultTemplate) {
        ForwardCommonConfigStore.defaultTemplate()
    } else {
        config.messageTemplate
    }.lineSequence().firstOrNull()?.trim().orEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "短信公共配置",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "设备识别号: ${config.deviceName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (isDefaultTemplate) {
                    "模板: 默认模板（留空自动使用）"
                } else {
                    "模板: 自定义模板"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (templatePreview.isNotBlank()) {
                Text(
                    text = "预览: $templatePreview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
            }
        }
    }
}

@Composable
private fun AppNotifyConfigCard(
    template: String,
    onEdit: () -> Unit,
) {
    val isDefaultTemplate = template.isBlank()
    val templatePreview = if (isDefaultTemplate) {
        appNotifyDefaultTemplate()
    } else {
        template
    }.lineSequence().firstOrNull()?.trim().orEmpty()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "应用通知配置",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isDefaultTemplate) "模板: 默认模板（留空自动使用）" else "模板: 自定义模板",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (templatePreview.isNotBlank()) {
                Text(
                    text = "预览: $templatePreview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
            }
        }
    }
}

@Composable
private fun ForwardCommonConfigDialog(
    currentConfig: ForwardCommonConfig,
    onDismiss: () -> Unit,
    onSave: (ForwardCommonConfig) -> Unit,
) {
    val context = LocalContext.current
    var deviceName by remember(currentConfig.deviceName) { mutableStateOf(currentConfig.deviceName) }
    var templateValue by remember(currentConfig.messageTemplate) {
        mutableStateOf(TextFieldValue(currentConfig.messageTemplate))
    }
    var templateFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val fillTemplateInteractionSource = remember { MutableInteractionSource() }
    val isFillTemplatePressed by fillTemplateInteractionSource.collectIsPressedAsState()
    var suppressNextClick by remember { mutableStateOf(false) }
    fun renderPreview(templateText: String): String {
        val previewConfig = currentConfig.copy(
            deviceName = deviceName.trim(),
            messageTemplate = templateText,
        )
        return ForwardCommonConfigStore.applyToMessage(
            context = context,
            msgInfo = buildSmsPreviewMessage(),
            config = previewConfig,
        ).content
    }
    var previewText by remember(currentConfig.deviceName, currentConfig.messageTemplate) {
        mutableStateOf(renderPreview(templateValue.text))
    }
    fun insertToken(token: String) {
        val start = templateValue.selection.start.coerceIn(0, templateValue.text.length)
        val end = templateValue.selection.end.coerceIn(0, templateValue.text.length)
        val newText = buildString {
            append(templateValue.text.substring(0, start))
            append(token)
            append(templateValue.text.substring(end))
        }
        val cursor = start + token.length
        templateValue = templateValue.copy(text = newText, selection = TextRange(cursor))
        if (!templateFocused) {
            previewText = renderPreview(templateValue.text)
        }
    }

    fun normalizeTokenDeletion(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text
        val oldSelection = oldValue.selection
        val newSelection = newValue.selection
        if (oldSelection.start != oldSelection.end) return newValue
        if (newText.length != oldText.length - 1) return newValue

        val oldCursor = oldSelection.start
        val isBackspace = newSelection.start == (oldCursor - 1).coerceAtLeast(0)
        val removeIndex = if (isBackspace) oldCursor - 1 else oldCursor
        if (removeIndex !in oldText.indices) return newValue

        val token = templateTokenRegex.findAll(oldText).firstOrNull { match ->
            removeIndex in match.range
        } ?: return newValue

        val start = token.range.first
        val endExclusive = token.range.last + 1
        val merged = oldText.removeRange(start, endExclusive)
        return TextFieldValue(
            text = merged,
            selection = TextRange(start.coerceAtMost(merged.length)),
        )
    }

    @Suppress("MagicNumber")
    LaunchedEffect(isFillTemplatePressed) {
        if (isFillTemplatePressed) {
            delay(10_000)
            if (isFillTemplatePressed) {
                suppressNextClick = true
                val fullTemplate = ForwardCommonConfigStore.fullInfoTemplate()
                templateValue = TextFieldValue(
                    fullTemplate,
                    selection = TextRange(fullTemplate.length),
                )
            }
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text("短信公共配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设备识别号") },
                    placeholder = { Text("默认读取系统 prop") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = templateValue,
                    onValueChange = { newValue ->
                        templateValue = normalizeTokenDeletion(templateValue, newValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .onFocusChanged { focusState ->
                            if (templateFocused && !focusState.isFocused) {
                                previewText = renderPreview(templateValue.text)
                            }
                            templateFocused = focusState.isFocused
                        },
                    label = { Text("转发信息模板") },
                    placeholder = { Text("留空使用默认模板") },
                    supportingText = { Text("Tip: 按需插入内容标签；可用变量见下方按钮") },
                )
                Text(
                    text = "效果预览：\n$previewText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (suppressNextClick) {
                                suppressNextClick = false
                                return@TextButton
                            }
                            val defaultTemplate = ForwardCommonConfigStore.defaultTemplate()
                            templateValue = TextFieldValue(
                                defaultTemplate,
                                selection = TextRange(defaultTemplate.length),
                            )
                            previewText = renderPreview(templateValue.text)
                        },
                        interactionSource = fillTemplateInteractionSource,
                    ) {
                        Text("填入默认模板")
                    }
                }
                HorizontalDivider()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(forwardTemplateVariables.size) { index ->
                        val variable = forwardTemplateVariables[index]
                        OutlinedButton(
                            onClick = { insertToken(variable.token) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(variable.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        currentConfig.copy(
                            deviceName = deviceName.trim(),
                            messageTemplate = templateValue.text,
                        ),
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AppNotifyTemplateDialog(
    currentTemplate: String,
    currentCommonConfig: ForwardCommonConfig,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var templateValue by remember(currentTemplate) { mutableStateOf(TextFieldValue(currentTemplate)) }
    var templateFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val fillTemplateInteractionSource = remember { MutableInteractionSource() }
    val isFillTemplatePressed by fillTemplateInteractionSource.collectIsPressedAsState()
    var suppressNextClick by remember { mutableStateOf(false) }
    fun renderPreview(templateText: String): String {
        val previewConfig = currentCommonConfig.copy(messageTemplate = templateText)
        return ForwardCommonConfigStore.applyToMessage(
            context = context,
            msgInfo = buildAppNotifyPreviewMessage(),
            config = previewConfig,
        ).content
    }
    var previewText by remember(currentTemplate, currentCommonConfig.deviceName) {
        mutableStateOf(renderPreview(templateValue.text))
    }

    fun insertToken(token: String) {
        val start = templateValue.selection.start.coerceIn(0, templateValue.text.length)
        val end = templateValue.selection.end.coerceIn(0, templateValue.text.length)
        val newText = buildString {
            append(templateValue.text.substring(0, start))
            append(token)
            append(templateValue.text.substring(end))
        }
        val cursor = start + token.length
        templateValue = templateValue.copy(text = newText, selection = TextRange(cursor))
        if (!templateFocused) {
            previewText = renderPreview(templateValue.text)
        }
    }

    fun normalizeTokenDeletion(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text
        val oldSelection = oldValue.selection
        val newSelection = newValue.selection
        if (oldSelection.start != oldSelection.end) return newValue
        if (newText.length != oldText.length - 1) return newValue

        val oldCursor = oldSelection.start
        val isBackspace = newSelection.start == (oldCursor - 1).coerceAtLeast(0)
        val removeIndex = if (isBackspace) oldCursor - 1 else oldCursor
        if (removeIndex !in oldText.indices) return newValue

        val token = templateTokenRegex.findAll(oldText).firstOrNull { match ->
            removeIndex in match.range
        } ?: return newValue

        val start = token.range.first
        val endExclusive = token.range.last + 1
        val merged = oldText.removeRange(start, endExclusive)
        return TextFieldValue(
            text = merged,
            selection = TextRange(start.coerceAtMost(merged.length)),
        )
    }

    @Suppress("MagicNumber")
    LaunchedEffect(isFillTemplatePressed) {
        if (isFillTemplatePressed) {
            delay(10_000)
            if (isFillTemplatePressed) {
                suppressNextClick = true
                val fullTemplate = appNotifyFullTemplate()
                templateValue = TextFieldValue(
                    fullTemplate,
                    selection = TextRange(fullTemplate.length),
                )
            }
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text("应用通知配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = templateValue,
                    onValueChange = { newValue ->
                        templateValue = normalizeTokenDeletion(templateValue, newValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .onFocusChanged { focusState ->
                            if (templateFocused && !focusState.isFocused) {
                                previewText = renderPreview(templateValue.text)
                            }
                            templateFocused = focusState.isFocused
                        },
                    label = { Text("应用通知转发模板") },
                    placeholder = { Text("留空使用默认模板") },
                    supportingText = { Text("Tip: 按需插入内容标签；可用变量见下方按钮") },
                )
                Text(
                    text = "效果预览：\n$previewText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (suppressNextClick) {
                                suppressNextClick = false
                                return@TextButton
                            }
                            val defaultTemplate = appNotifyDefaultTemplate()
                            templateValue = TextFieldValue(
                                defaultTemplate,
                                selection = TextRange(defaultTemplate.length),
                            )
                            previewText = renderPreview(templateValue.text)
                        },
                        interactionSource = fillTemplateInteractionSource,
                    ) {
                        Text("填入默认模板")
                    }
                }
                HorizontalDivider()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(appNotifyTemplateVariables.size) { index ->
                        val variable = appNotifyTemplateVariables[index]
                        OutlinedButton(
                            onClick = { insertToken(variable.token) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(variable.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(templateValue.text) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
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
                    text = sender.name.ifEmpty { getSenderTypeName(sender.type) },
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
        SenderType.WEWORK_ROBOT -> "企微群机器人"
        SenderType.WEWORK_AGENT -> "企微应用"
        SenderType.SERVERCHAN -> "Server酱"
        SenderType.TELEGRAM -> "Telegram机器人"
        SenderType.SMS -> if (BuildConfig.ENABLE_SMS_CHANNEL) "短信" else "短信(不可用)"
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

package com.github.magisk317.smscode.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TransitionGateScreen(
    relayInstalled: Boolean,
    onStartAutoMigration: () -> Unit,
    onUpgradeToLite: () -> Unit,
    onManualBackupExport: () -> Unit,
) {
    var showMigrateDialog by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "过渡版本",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "当前仅支持两条路径：迁移到信驿 Relay，或升级到验证码精简版。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { showMigrateDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (relayInstalled) "切到信驿 Relay 并导入数据" else "下载信驿 Relay 并迁移")
            }
            Button(onClick = onUpgradeToLite, modifier = Modifier.fillMaxWidth()) {
                Text("升级到验证码精简版")
            }
        }
    }
    if (showMigrateDialog) {
        AlertDialog(
            onDismissRequest = { showMigrateDialog = false },
            title = { Text("迁移到信驿 Relay") },
            text = { Text("默认推荐自动导入；若失败可导出备份在新应用手动导入。") },
            confirmButton = {
                Button(
                    onClick = {
                        showMigrateDialog = false
                        onStartAutoMigration()
                    },
                ) {
                    Text("自动导入")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMigrateDialog = false
                        onManualBackupExport()
                    },
                ) {
                    Text("手动导出备份")
                }
            },
        )
    }
}

@Composable
fun ExpiredBlockScreen(
    onOpenRelay: () -> Unit,
    onOpenUninstall: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "过渡版本已到期",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "该版本已不可继续使用，请迁移到信驿 Relay 或卸载当前应用。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenRelay, modifier = Modifier.fillMaxWidth()) {
                Text("前往信驿 Relay")
            }
            OutlinedButton(onClick = onOpenUninstall, modifier = Modifier.fillMaxWidth()) {
                Text("卸载当前应用")
            }
        }
    }
}

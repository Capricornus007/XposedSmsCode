package com.github.magisk317.smscode.ui.sender.forms

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DraftExitDialog(
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("离开当前页面？") },
        text = { Text("可以保存为草稿，或放弃本次修改。") },
        confirmButton = {
            TextButton(onClick = onSaveDraft) {
                Text("保存草稿")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("放弃")
            }
        },
    )
}

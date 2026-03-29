package com.github.magisk317.smscode.ui.theme

import androidx.compose.runtime.Composable
import io.github.magisk317.uikit.theme.MagiskUiKitTheme
import io.github.magisk317.uikit.theme.UiKitStyle

@Composable
fun AppTheme(
    themeMode: Int,
    uiKitStyle: Int,
    content: @Composable () -> Unit,
) {
    MagiskUiKitTheme(
        themeMode = themeMode,
        uiKitStyle = UiKitStyle.fromValue(uiKitStyle),
        content = content,
    )
}

package com.tianma.xsmscode.ui.app.base

import android.app.Activity
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

fun applyEdgeToEdge(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

@Composable
fun SystemBarsScrim(
    color: Color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(color, Color.Transparent)
                    )
                )
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, color)
                    )
                )
        )
    }
}

@Composable
fun UpdateSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) {
        return
    }
    val window = (view.context as Activity).window
    SideEffect {
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

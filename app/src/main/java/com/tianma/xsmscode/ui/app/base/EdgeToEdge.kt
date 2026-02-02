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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight

fun applyEdgeToEdge(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}



@Composable
fun SystemBarsScrim(
    hazeState: HazeState,
    hazeStyle: HazeStyle
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .hazeEffect(hazeState, hazeStyle)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .align(Alignment.BottomStart)
                .hazeEffect(hazeState, hazeStyle)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
        )
    }
}

@Composable
fun rememberHazeStyle(): HazeStyle {
    return HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surface,
        tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        blurRadius = 30.dp,
        noiseFactor = 0f
    )
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

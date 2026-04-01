package com.github.magisk317.smscode.ui.home

import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle

@Composable
internal fun OverviewScreenMaterial(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
) {
    OverviewScreenShared(
        hazeState = hazeState,
        hazeStyle = hazeStyle,
    )
}

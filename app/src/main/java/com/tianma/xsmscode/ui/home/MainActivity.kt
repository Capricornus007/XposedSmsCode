package com.tianma.xsmscode.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.ui.nav.SmsCodeNavHost
import org.koin.androidx.compose.koinViewModel
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            val navController = rememberNavController()

            // Circular Reveal Animation State
            var currentThemeMode by remember { mutableIntStateOf(themeState.mode) }
            var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
            val revealAnim = remember { Animatable(0f) }
            var isAnimating by remember { mutableStateOf(false) }
            var animationCenter by remember { mutableStateOf(Offset.Zero) }
            val view = LocalView.current

            // Effect to trigger logic when ThemeState changes
            LaunchedEffect(themeState) {
                if (themeState.mode != currentThemeMode) {
                    // 1. Capture Screenshot of current state (Old Theme)
                    try {
                         // We need to verify if the view is laid out.
                         if (view.width > 0 && view.height > 0) {
                             val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                             val canvas = android.graphics.Canvas(bitmap)
                             view.draw(canvas)
                             screenshotBitmap = bitmap
                             
                             // 2. Setup Animation Parameters
                             val centerX = if (themeState.centerX >= 0) themeState.centerX else view.width / 2f
                             val centerY = if (themeState.centerY >= 0) themeState.centerY else view.height / 2f
                             animationCenter = Offset(centerX, centerY)
                             
                             // 3. Update Theme to NEW Mode (Re-renders UI behind)
                             isAnimating = true
                             currentThemeMode = themeState.mode
                             
                             // 4. Start Animation
                             revealAnim.snapTo(0f)
                             revealAnim.animateTo(
                                 targetValue = 1f,
                                 animationSpec = tween(durationMillis = 600)
                             )
                             
                             // 5. Cleanup
                             isAnimating = false
                             screenshotBitmap = null
                         } else {
                             // Fallback if view not ready
                             currentThemeMode = themeState.mode
                         }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback on error
                        currentThemeMode = themeState.mode
                    }
                } else {
                    // Initial load
                    currentThemeMode = themeState.mode
                }
            }
            
            // Collect navigation events
            LaunchedEffect(viewModel.eventsFlow) {
                viewModel.eventsFlow.collect { event ->
                    when (event) {
                        is SettingsEvent.NavigateToRules -> navController.navigate(com.tianma.xsmscode.ui.nav.RulesListRoute)
                        is SettingsEvent.NavigateToRecords -> navController.navigate(com.tianma.xsmscode.ui.nav.RecordsRoute)
                        else -> {}
                    }
                }
            }

            AppTheme(themeMode = currentThemeMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LaunchedEffect(Unit) {
                        viewModel.setInternalFilesWritable()
                    }
                    LaunchedEffect(intent) {
                        viewModel.handleArguments(intent.extras)
                    }
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                         SmsCodeNavHost(
                            navController = navController,
                            onBack = { finish() }
                        )
                        
                        // Overlay for Circular Reveal
                        if (isAnimating && screenshotBitmap != null) {
                            val bitmap = screenshotBitmap!!.asImageBitmap()
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        // Use Offscreen to allow BlendMode.Clear to punch a hole
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent() // Draw the Old Screenshot
                                        
                                        // Calculate specific radius for time t
                                        val maxRadius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
                                        val radius = maxRadius * revealAnim.value
                                        
                                        // Draw a transparent circle to reveal the new content underneath
                                        drawCircle(
                                            color = androidx.compose.ui.graphics.Color.Transparent,
                                            radius = radius,
                                            center = animationCenter,
                                            blendMode = BlendMode.Clear
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AppTheme(
        themeMode: Int,
        content: @Composable () -> Unit
    ) {
        val darkTheme = when (themeMode) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
            content = content
        )
    }
}

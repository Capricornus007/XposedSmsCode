package com.tianma.xsmscode.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.github.tianma8023.xposed.smscode.R
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.tianma.xsmscode.common.utils.SPUtils
import com.tianma.xsmscode.ui.app.base.UpdateSystemBars
import com.tianma.xsmscode.ui.app.base.applyEdgeToEdge
import com.tianma.xsmscode.ui.app.base.rememberHazeStyle
import com.tianma.xsmscode.ui.nav.SmsCodeNavHost
import com.tianma.xsmscode.ui.privacy.PrivacyPolicyPage
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private lateinit var appUpdateManager: AppUpdateManager
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            android.widget.Toast.makeText(
                this,
                getString(R.string.check_update_failed),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            appUpdateManager.completeUpdate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(window)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener(installStateUpdatedListener)

        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            val navController = rememberNavController()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
            var showPrivacyPolicyPage by remember { mutableStateOf(false) }

            // Circular Reveal Animation State
            var currentThemeMode by remember { mutableIntStateOf(themeState.mode) }
            var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
            val revealAnim = remember { Animatable(0f) }
            var isAnimating by remember { mutableStateOf(false) }
            var animationCenter by remember { mutableStateOf(Offset.Zero) }
            val view = LocalView.current
            var requestedTab by remember { mutableStateOf<Any?>(null) }

            LaunchedEffect(Unit) {
                if (!SPUtils.isPrivacyPolicyAccepted(context)) {
                    showPrivacyPolicyDialog = true
                }
            }

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
                                animationSpec = tween(durationMillis = 600),
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
                        is SettingsEvent.ShowPrivacyPolicy -> showPrivacyPolicyDialog = true
                        is SettingsEvent.NavigateToRules -> requestedTab = com.tianma.xsmscode.ui.nav.FaqRoute
                        is SettingsEvent.NavigateToRecords -> requestedTab = com.tianma.xsmscode.ui.nav.RecordsRoute
                        is SettingsEvent.StartPlayUpdate -> requestPlayUpdate()
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
                        val hazeState = remember { HazeState() }
                        val hazeStyle = rememberHazeStyle()
                        SmsCodeNavHost(
                            navController = navController,
                            onBack = { finish() },
                            initialTab = requestedTab,
                            onInitialTabConsumed = { requestedTab = null },
                            modifier = Modifier,
                            hazeState = hazeState,
                            hazeStyle = hazeStyle,
                        )

                        if (showPrivacyPolicyDialog) {
                            PrivacyPolicyDialog(
                                onDismiss = { showPrivacyPolicyDialog = false },
                                onConfirm = {
                                    scope.launch { SPUtils.setPrivacyPolicyAccepted(context, true) }
                                    showPrivacyPolicyDialog = false
                                },
                                onCancel = {
                                    scope.launch { SPUtils.setPrivacyPolicyAccepted(context, false) }
                                    showPrivacyPolicyDialog = false
                                    finish()
                                },
                                onViewPolicy = { showPrivacyPolicyPage = true },
                            )
                        }

                        if (showPrivacyPolicyPage) {
                            PrivacyPolicyPage(onDismiss = { showPrivacyPolicyPage = false })
                        }

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
                                            blendMode = BlendMode.Clear,
                                        )
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startUpdateFlow(info)
            } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }

    private fun requestPlayUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    startUpdateFlow(info)
                }

                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    startUpdateFlow(info)
                }

                else -> {
                    android.widget.Toast.makeText(
                        this,
                        getString(R.string.app_already_newest),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }.addOnFailureListener {
            android.widget.Toast.makeText(
                this,
                getString(R.string.check_update_failed),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun startUpdateFlow(info: com.google.android.play.core.appupdate.AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                info,
                updateLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
            )
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                this,
                getString(R.string.check_update_failed),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun AppTheme(themeMode: Int, content: @Composable () -> Unit) {
        val darkTheme = when (themeMode) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }

        UpdateSystemBars(darkTheme)

        // Material 3 Expressive Theme Implementation
        // Note: MaterialExpressiveTheme uses its own shape and typography defaults
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}

package com.tianma.xsmscode.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.ui.nav.SmsCodeNavHost
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle(0)
            val navController = rememberNavController()
            
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

            AppTheme(themeMode = themeMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LaunchedEffect(Unit) {
                        viewModel.setInternalFilesWritable()
                    }
                    // Handle initial arguments if any
                    LaunchedEffect(intent) {
                        viewModel.handleArguments(intent.extras)
                    }
                    
                    SmsCodeNavHost(
                        navController = navController,
                        onBack = { finish() }
                    )
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

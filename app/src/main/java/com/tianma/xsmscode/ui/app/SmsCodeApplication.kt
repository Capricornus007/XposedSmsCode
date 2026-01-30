package com.tianma.xsmscode.ui.app

import android.app.Application
import com.tianma.xsmscode.feature.migrate.TransitionTask
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import com.tianma.xsmscode.di.appModule

class SmsCodeApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (com.github.tianma8023.xposed.smscode.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidLogger()
            androidContext(this@SmsCodeApplication)
            modules(appModule)
        }
        syncPreferences()
        performTransitionTask()
    }

    private fun syncPreferences() {
        applicationScope.launch {
            AppPreferencesDataStore.syncToSharedPrefs(this@SmsCodeApplication)
            AppPreferencesDataStore.ensureReadable(this@SmsCodeApplication)
        }
    }

    private fun performTransitionTask() {
        applicationScope.launch {
            TransitionTask(this@SmsCodeApplication).run()
        }
    }
}

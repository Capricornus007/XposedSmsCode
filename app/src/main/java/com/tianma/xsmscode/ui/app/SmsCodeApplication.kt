package com.tianma.xsmscode.ui.app

import android.app.Application
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.di.appModule
import com.tianma.xsmscode.feature.migrate.TransitionTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import android.app.Activity
import android.os.Bundle
import timber.log.Timber

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
        registerLicenseActivityKiller()
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

    private fun registerLicenseActivityKiller() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                if (activity.javaClass.name == "com.pairip.licensecheck.LicenseActivity") {
                    runCatching {
                        Timber.w("Detected com.pairip.licensecheck.LicenseActivity. Finishing it to prevent gray screen.")
                        activity.finish()
                    }
                        .onFailure { Timber.e(it, "Failed to finish LicenseActivity") }
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

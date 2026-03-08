package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction

class KillMeAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
) : CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        killMe()
        return null
    }

    private fun killMe() {
        if (!PrefsReader.killMeEnabled(mPluginContext)) return
        if (!hasKillBackgroundPermission()) {
            XLog.w(
                "Skip KillMeAction: missing permission %s in process %s",
                Manifest.permission.KILL_BACKGROUND_PROCESSES,
                mPhoneContext.packageName,
            )
            return
        }
        killBackgroundProcess(BuildConfig.APPLICATION_ID)
    }

    private fun hasKillBackgroundPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            mPhoneContext,
            Manifest.permission.KILL_BACKGROUND_PROCESSES,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun killBackgroundProcess(packageName: String) {
        try {
            val activityManager = mPluginContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            activityManager?.let {
                it.killBackgroundProcesses(packageName)
                XLog.d("Kill %s background process succeed", packageName)
            }
        } catch (e: SecurityException) {
            XLog.w("Skip KillMeAction for %s: %s", packageName, e.message ?: "security denied")
        } catch (e: Throwable) {
            XLog.e("Error occurs when kill background process %s", packageName, e)
        }
    }
}

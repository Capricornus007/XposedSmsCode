package com.tianma.xsmscode.xp.hook.code.action.impl

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.content.Context
import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.PrefsReader
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.CallableAction

class KillMeAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        killMe()
        return null
    }

    private fun killMe() {
        if (!PrefsReader.killMeEnabled(mPluginContext)) return
        if (requestSelfKillInAppProcess()) return
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

    private fun requestSelfKillInAppProcess(): Boolean {
        return runCatching {
            val token = PrefsReader.getIpcToken(mPluginContext)
            if (token.isBlank()) {
                XLog.w("Skip KillMeAction IPC: token is empty")
                return false
            }
            val intent = Intent(PrefConst.ACTION_KILL_ME).apply {
                setPackage(mPluginContext.packageName)
                putExtra("ipc_token", token)
                putExtra("event_id", "kill_${System.currentTimeMillis().toString(36)}")
                putExtra("forward_source", "kill_me_action")
            }
            mPluginContext.sendBroadcast(intent)
            XLog.i("KillMeAction: sent self-kill IPC to app process")
            true
        }.getOrElse { error ->
            XLog.w("KillMeAction IPC failed: %s", error.message ?: error.javaClass.simpleName)
            false
        }
    }

    private fun hasKillBackgroundPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            mPhoneContext,
            Manifest.permission.KILL_BACKGROUND_PROCESSES,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * android.app.ActivityManager#killBackgroundProcess()
     */
    @SuppressLint("MissingPermission")
    private fun killBackgroundProcess(packageName: String) {
        try {
            val activityManager = mPluginContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            activityManager?.let {
                it.killBackgroundProcesses(packageName)
                XLog.d("Kill %s background process succeed", packageName)
            }
        } catch (e: SecurityException) {
            // Permission may be absent in some ROM/process combinations; skip without noisy stack trace.
            XLog.w("Skip KillMeAction for %s: %s", packageName, e.message ?: "security denied")
        } catch (e: Throwable) {
            XLog.e("Error occurs when kill background process %s", packageName, e)
        }
    }
}

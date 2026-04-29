package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.github.magisk317.smscode.receiver.KillSelfControlReceiver
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import io.github.magisk317.smscode.xposed.utils.XLog
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
        // HyperOS 3 adaptation:
        // model=25113PN0EC, build=OS3.0.44.0.WPCCNXM, device=pudding (Android 16 / SDK 36)
        // Provider self-kill is the primary strategy on this ROM.
        if (requestSelfKillPrimary()) {
            return
        }
        XLog.w("KillMeAction: provider self-kill failed for process %s", mPhoneContext.packageName)
    }

    private fun requestSelfKillPrimary(): Boolean {
        return try {
            val token = PrefsReader.getIpcToken(mPluginContext)
            if (token.isBlank()) return false
            val intent = Intent(KillSelfControlReceiver.ACTION_KILL_SELF).apply {
                setClassName(mPluginContext.packageName, KillSelfControlReceiver::class.java.name)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(KillSelfControlReceiver.EXTRA_DELAY_MS, 80L)
                putExtra(KillSelfControlReceiver.EXTRA_IPC_TOKEN, token)
            }
            mPluginContext.sendBroadcast(intent)
            XLog.w("KillMeAction primary requested via KillSelfControlReceiver")
            true
        } catch (e: Throwable) {
            XLog.w("KillMeAction primary failed: %s", e.message ?: e.javaClass.simpleName)
            false
        }
    }
}

package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import com.github.magisk317.smscode.data.prefs.PrefsProvider
import com.github.magisk317.smscode.common.utils.PrefsReader
import io.github.magisk317.smscode.core.utils.XLog
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
            val extras = Bundle().apply { putLong(PrefsProvider.EXTRA_DELAY_MS, 80L) }
            val result = mPluginContext.contentResolver.call(
                PrefsProvider.BOOL_URI,
                PrefsProvider.METHOD_KILL_SELF,
                null,
                extras,
            )
            val ok = result?.getBoolean(PrefsProvider.EXTRA_OK, false) ?: false
            if (ok) {
                XLog.w("KillMeAction primary requested via PrefsProvider")
            }
            ok
        } catch (e: Throwable) {
            XLog.w("KillMeAction primary failed: %s", e.message ?: e.javaClass.simpleName)
            false
        }
    }
}

package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.feature.sync.SyncManager
import com.tianma.xsmscode.xp.hook.code.action.CallableAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 同步验证码 Action
 */
class SyncSmsAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        XLog.i("SyncSmsAction: Starting execution for code=${mSmsMsg.smsCode}")
        XLog.d("Executing SyncSmsAction")
        // Use GlobalScope or a long-running scope because this is a short-lived action in a system process
        // Dispatchers.IO is safe here as it won't block the caller thread
        @Suppress("OPT_IN_USAGE")
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                SyncManager.pushSmsToGroup(
                    context = mPluginContext,  // Use pluginContext to read SharedPreferences
                    code = mSmsMsg.smsCode ?: "",
                    sender = mSmsMsg.sender ?: "",
                    body = mSmsMsg.body,
                    timestamp = mSmsMsg.date,
                    company = mSmsMsg.company,
                    packageName = mSmsMsg.packageName
                )
            } catch (e: Exception) {
                XLog.e("SyncSmsAction failed", e)
            }
        }
        return null
    }
}

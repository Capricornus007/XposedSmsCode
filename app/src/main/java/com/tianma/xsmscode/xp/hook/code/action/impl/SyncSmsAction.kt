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
        // Use runBlocking to ensure sync completes before returning
        kotlinx.coroutines.runBlocking {
            try {
                SyncManager.pushSmsToGroup(
                    context = mPluginContext,  // 使用 mPluginContext 而非 mPhoneContext
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

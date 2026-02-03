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
        XLog.d("Executing SyncSmsAction")
        // Use IO dispatcher for network operations
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SyncManager.pushSmsToGroup(
                    context = mPhoneContext,
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

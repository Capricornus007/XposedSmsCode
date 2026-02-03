package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import com.tianma.xsmscode.common.utils.ClipboardUtils
import com.tianma.xsmscode.common.utils.PrefsReader
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.RunnableAction

/**
 * 将验证码复制到剪切板
 */
class CopyToClipboardAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg
) : RunnableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (PrefsReader.copyToClipboardEnabled(mPluginContext)) {
            copyToClipboard()
        }
        return null
    }

    private fun copyToClipboard() {
        try {
            XLog.d("Attempting to copy code to clipboard with context: $mPhoneContext")
            ClipboardUtils.copyToClipboard(mPhoneContext, mSmsMsg.smsCode)
        } catch (e: Exception) {
            XLog.e("Failed to copy to clipboard", e)
        }
    }
}

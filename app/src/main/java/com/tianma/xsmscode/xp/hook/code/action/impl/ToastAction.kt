package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.utils.PrefsReader
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.RunnableAction

/**
 * 显示验证码Toast
 */
class ToastAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg
) : RunnableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (PrefsReader.shouldShowToast(mPluginContext)) {
            showCodeToast()
        }
        return null
    }

    private fun showCodeToast() {
        val text = mPluginContext.getString(R.string.current_sms_code, mSmsMsg.smsCode)
        Toast.makeText(mPhoneContext, text, Toast.LENGTH_LONG).show()
    }
}

package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.xp.hook.code.helper.InputHelper
import io.github.magisk317.smscode.core.utils.XLog
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.hook.code.action.RunnableAction
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 显示验证码Toast
 */
class ToastAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    RunnableAction(pluginContext, phoneContext, smsMsg) {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun action(): Bundle? {
        if (PrefsReader.shouldShowToast(mPluginContext)) {
            showCodeToast()
        }
        return null
    }

    private fun showCodeToast() {
        val smsCode = mSmsMsg.smsCode.orEmpty()
        val text = mPluginContext.getString(R.string.current_sms_code, smsCode)
        val toast = Toast.makeText(mPhoneContext, text, Toast.LENGTH_LONG)
        XLog.w(
            "Diag toast request: pkg=%s uid=%d code_len=%d text_len=%d",
            mPhoneContext.packageName,
            android.os.Process.myUid(),
            smsCode.length,
            text.length,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val shown = AtomicBoolean(false)
            toast.addCallback(
                object : Toast.Callback() {
                    override fun onToastShown() {
                        shown.set(true)
                        XLog.w(
                            "Diag toast shown: pkg=%s code_len=%d",
                            mPhoneContext.packageName,
                            smsCode.length,
                        )
                    }

                    override fun onToastHidden() {
                        XLog.w(
                            "Diag toast hidden: pkg=%s code_len=%d",
                            mPhoneContext.packageName,
                            smsCode.length,
                        )
                    }
                },
            )
            mainHandler.postDelayed(
                {
                    if (!shown.get()) {
                        XLog.w(
                            "Diag toast fallback via system receiver: pkg=%s code_len=%d",
                            mPhoneContext.packageName,
                            smsCode.length,
                        )
                        InputHelper.sendToast(mPhoneContext, text, Toast.LENGTH_LONG)
                    }
                },
                TOAST_FALLBACK_DELAY_MS,
            )
        }
        toast.show()
    }

    companion object {
        private const val TOAST_FALLBACK_DELAY_MS = 1500L
    }
}

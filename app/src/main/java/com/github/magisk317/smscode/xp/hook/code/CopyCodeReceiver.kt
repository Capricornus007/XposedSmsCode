package com.github.magisk317.smscode.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.github.magisk317.smscode.common.utils.ClipboardUtils
import com.github.magisk317.smscode.core.R
import com.github.tianma8023.xposed.smscode.BuildConfig

/**
 * Receiver for copy code when notification clicked
 */
class CopyCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (ACTION_COPY_CODE == action) {
            val smsCode = intent.getStringExtra(EXTRA_KEY_CODE)
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

            // cancel notification
            if (notificationId != -1) {
                val manager = context.getSystemService(
                    Context.NOTIFICATION_SERVICE,
                ) as android.app.NotificationManager?
                manager?.cancel(notificationId)
            }
            // copy to clipboard
            smsCode?.let {
                ClipboardUtils.copyToClipboard(context, it)
                // show toast
                showToast(context, it)
            }
        }
    }

    private fun showToast(context: Context, smsCode: String) {
        val text = context.getString(R.string.prompt_sms_code_copied, smsCode)
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val ACTION_COPY_CODE = "${BuildConfig.APPLICATION_ID}.ACTION_COPY_CODE"
        private const val EXTRA_KEY_CODE = "extra_key_code"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        private val instance: CopyCodeReceiver by lazy { CopyCodeReceiver() }

        @JvmStatic
        fun createIntent(context: Context, smsCode: String?, notificationId: Int): Intent =
            Intent(context, CopyCodeReceiver::class.java).apply {
                action = ACTION_COPY_CODE
                putExtra(EXTRA_KEY_CODE, smsCode)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }

        @JvmStatic
        fun registerMe(context: Context) {
            val filter = IntentFilter()
            filter.addAction(ACTION_COPY_CODE)
            ContextCompat.registerReceiver(context, instance, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }
}

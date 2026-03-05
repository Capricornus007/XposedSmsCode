package com.github.magisk317.smscode.transition

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.github.tianma8023.xposed.smscode.BuildConfig

object TransitionExpiryScheduler {
    private const val REQUEST_CODE_DAILY = 31802

    fun scheduleDailyCheck(context: Context) {
        if (!BuildConfig.IS_TRANSITION_BUILD) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        val triggerAt = now + AlarmManager.INTERVAL_DAY
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            AlarmManager.INTERVAL_DAY,
            buildDailyPendingIntent(context),
        )
    }

    private fun buildDailyPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TransitionExpiryReceiver::class.java).apply {
            action = TransitionExpiryReceiver.ACTION_DAILY_CHECK
            `package` = context.packageName
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

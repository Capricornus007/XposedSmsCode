package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import com.github.magisk317.smscode.common.utils.PrefsReader
import io.github.magisk317.smscode.verification.VerificationPrefs

internal class SmsCodeVerificationPrefs(
    private val context: Context,
) : VerificationPrefs {
    override fun showNotification(): Boolean = PrefsReader.showCodeNotification(context)

    override fun autoCancelNotification(): Boolean = PrefsReader.autoCancelCodeNotification(context)

    override fun notificationRetentionMs(): Long = PrefsReader.getNotificationRetentionTime(context) * 1000L

    override fun autoInputEnabled(): Boolean = PrefsReader.autoInputCodeEnabled(context)

    override fun autoInputDelayMs(): Long = PrefsReader.getAutoInputCodeDelay(context) * 1000L

    override fun copyToClipboardEnabled(): Boolean = PrefsReader.copyToClipboardEnabled(context)

    override fun showToast(): Boolean = PrefsReader.shouldShowToast(context)

    override fun recordSmsEnabled(): Boolean = PrefsReader.recordCodeSmsEnabled(context)

    override fun blockSmsEnabled(): Boolean = PrefsReader.blockSmsEnabled(context)

    override fun markAsReadEnabled(): Boolean = PrefsReader.markAsReadEnabled(context)

    override fun deleteSmsEnabled(): Boolean = PrefsReader.deleteSmsEnabled(context)

    override fun deduplicateSmsEnabled(): Boolean = PrefsReader.deduplicateSms(context)
}

package io.github.magisk317.relay.platform.ipc

import android.content.Context
import com.github.magisk317.smscode.runtime.AppPrefsFacade
import io.github.magisk317.smscode.verification.VerificationPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal class AppSmsCodeVerificationPrefs(
    private val context: Context,
) : VerificationPrefs {
    override fun showNotification(): Boolean = read { AppPrefsFacade.showCodeNotification(context) }

    override fun autoCancelNotification(): Boolean = read { AppPrefsFacade.autoCancelCodeNotification(context) }

    override fun notificationRetentionMs(): Long = read { AppPrefsFacade.getNotificationRetentionTime(context) } * 1000L

    override fun autoInputEnabled(): Boolean = read { AppPrefsFacade.autoInputCodeEnabled(context) }

    override fun autoInputDelayMs(): Long = read { AppPrefsFacade.getAutoInputCodeDelay(context) }

    override fun inputIntervalMs(): Long = read { AppPrefsFacade.getAutoInputCodeIntervalMs(context) }

    override fun copyToClipboardEnabled(): Boolean = read { AppPrefsFacade.copyToClipboardEnabled(context) }

    override fun showToast(): Boolean = read { AppPrefsFacade.shouldShowToast(context) }

    override fun recordSmsEnabled(): Boolean = read { AppPrefsFacade.recordCodeSmsEnabled(context) }

    override fun blockSmsEnabled(): Boolean = read { AppPrefsFacade.blockSmsEnabled(context) }

    override fun markAsReadEnabled(): Boolean = read { AppPrefsFacade.markAsReadEnabled(context) }

    override fun deleteSmsEnabled(): Boolean = read { AppPrefsFacade.deleteSmsEnabled(context) }

    override fun deduplicateSmsEnabled(): Boolean = read { AppPrefsFacade.deduplicateSms(context) }

    private fun <T> read(block: suspend () -> T): T = runBlocking(Dispatchers.IO) {
        block()
    }
}

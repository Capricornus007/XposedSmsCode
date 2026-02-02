package com.tianma.xsmscode.common.receiver

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getLong("download_id", -1L)
        if (savedId != downloadId) return

        val autoInstall = prefs.getBoolean("auto_install", false)
        if (!autoInstall) return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val apkUri: Uri = dm.getUriForDownloadedFile(downloadId) ?: return

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}

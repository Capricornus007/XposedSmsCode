package com.tianma.xsmscode.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R

class UpdateInstalledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.data ?: return
        val packageName = data.schemeSpecificPart ?: return
        if (packageName != BuildConfig.APPLICATION_ID) return

        Toast.makeText(context, context.getString(R.string.update_completed), Toast.LENGTH_SHORT).show()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
    }
}

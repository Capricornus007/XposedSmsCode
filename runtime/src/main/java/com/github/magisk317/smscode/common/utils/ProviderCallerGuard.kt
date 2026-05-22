package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.Process

object ProviderCallerGuard {
    fun isCallerAllowed(context: Context): Boolean {
        val uid = Binder.getCallingUid()
        if (isPrivilegedUid(uid, context.applicationInfo?.uid)) return true

        val packages = runCatching {
            context.packageManager.getPackagesForUid(uid)
        }.getOrNull() ?: return false

        return packages.any { packageName ->
            runCatching {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                isSystemPackageFlags(info.flags)
            }.getOrDefault(false)
        }
    }

    fun isPrivilegedUid(uid: Int, appUid: Int?): Boolean {
        return uid == Process.SYSTEM_UID ||
            uid == Process.PHONE_UID ||
            (appUid != null && uid == appUid)
    }

    fun isSystemPackageFlags(flags: Int): Boolean {
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return flags and systemFlags != 0
    }
}

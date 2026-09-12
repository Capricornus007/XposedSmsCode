package io.github.capricornus007.smscode.common.constant

import android.content.Context
import android.content.pm.PackageManager
import io.github.capricornus007.smscode.storage.BuildConfig

object TransitionConst {
    const val TARGET_RELAY_PACKAGE = "io.github.capricornus007.xinyi.relay"
    val TARGET_RELAY_URL: String = BuildConfig.B_DOWNLOAD_URL
    const val MIGRATED_REASON_CODE = "migrated_to_xinyi_relay"

    fun isRelayInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TARGET_RELAY_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun isRestrictedBuild(): Boolean = BuildConfig.IS_LITE_BUILD
}

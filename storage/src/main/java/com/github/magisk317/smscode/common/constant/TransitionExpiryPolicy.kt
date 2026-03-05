package com.github.magisk317.smscode.common.constant

import com.github.magisk317.smscode.storage.BuildConfig

object TransitionExpiryPolicy {
    val buildTimeUtcMs: Long = BuildConfig.TRANSITION_BUILD_TIME_UTC_MS
    val expiryDays: Int = BuildConfig.TRANSITION_EXPIRY_DAYS

    val expiresAtUtcMs: Long by lazy {
        if (buildTimeUtcMs <= 0L) {
            Long.MAX_VALUE
        } else {
            buildTimeUtcMs + expiryDays * 24L * 60L * 60L * 1000L
        }
    }

    fun isTransitionBuild(): Boolean = BuildConfig.IS_TRANSITION_BUILD

    fun isLiteBuild(): Boolean = BuildConfig.IS_LITE_BUILD

    fun isExpired(nowUtcMs: Long = System.currentTimeMillis()): Boolean {
        if (!isTransitionBuild()) return false
        return nowUtcMs >= expiresAtUtcMs
    }
}

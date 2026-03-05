package com.github.magisk317.smscode.feature.migrate.sp

import android.content.Context
import com.github.magisk317.smscode.common.utils.SPUtils
import com.github.magisk317.smscode.feature.migrate.ITransition

/**
 * SharedPreferences related data migration
 */
class PreferencesTransition(private val mContext: Context) : ITransition {

    override suspend fun shouldTransit(): Boolean {
        val localVersion = SPUtils.getLocalVersionCode(mContext)
        return false
    }

    override suspend fun doTransition(): Boolean = false

    companion object {
        private const val VERSION_CODE_16 = 16
    }
}

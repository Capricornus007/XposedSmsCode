package com.tianma.xsmscode.feature.migrate.sp

import android.content.Context
import com.tianma.xsmscode.common.utils.SPUtils
import com.tianma.xsmscode.feature.migrate.ITransition

/**
 * SharedPreferences related data migration
 */
class PreferencesTransition(private val mContext: Context) : ITransition {

    override suspend fun shouldTransit(): Boolean {
        val localVersion = SPUtils.getLocalVersionCode(mContext)
        return false
    }

    override suspend fun doTransition(): Boolean {
        return false
    }

    companion object {
        private const val VERSION_CODE_16 = 16
    }
}

package com.tianma.xsmscode.feature.migrate.db

import android.content.Context
import com.tianma.xsmscode.feature.migrate.ITransition
import com.tianma.xsmscode.ui.record.CodeRecordRestoreManager

class DBTransition(private val mContext: Context) : ITransition {

    override suspend fun shouldTransit(): Boolean {
        val recordFiles = CodeRecordRestoreManager.getRecordFiles(mContext)
        return recordFiles != null && recordFiles.isNotEmpty()
    }

    override suspend fun doTransition(): Boolean = CodeRecordRestoreManager.importToDatabase(mContext)
}

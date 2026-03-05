package com.github.magisk317.smscode.feature.migrate.db

import android.content.Context
import com.github.magisk317.smscode.feature.migrate.ITransition
import com.github.magisk317.smscode.ui.record.CodeRecordRestoreManager

class DBTransition(private val mContext: Context) : ITransition {

    override suspend fun shouldTransit(): Boolean {
        val recordFiles = CodeRecordRestoreManager.getRecordFiles(mContext)
        return recordFiles != null && recordFiles.isNotEmpty()
    }

    override suspend fun doTransition(): Boolean = CodeRecordRestoreManager.importToDatabase(mContext)
}

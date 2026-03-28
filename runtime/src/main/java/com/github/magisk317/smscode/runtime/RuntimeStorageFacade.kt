package com.github.magisk317.smscode.runtime

import android.content.Context
import com.github.magisk317.smscode.data.db.AppDatabase
import com.github.magisk317.smscode.data.db.DBManager

object RuntimeStorageFacade {
    fun appDatabase(context: Context): AppDatabase = AppDatabase.getInstance(context)

    fun dbManager(context: Context): DBManager = DBManager.get(context)
}

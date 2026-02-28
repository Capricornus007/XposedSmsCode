package com.tianma.xsmscode.di

import com.tianma.xsmscode.data.db.AppDatabase
import com.tianma.xsmscode.ui.home.AppConfigViewModel
import com.tianma.xsmscode.ui.home.SettingsViewModel
import com.tianma.xsmscode.ui.record.CodeRecordViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // Database
    single { AppDatabase.getInstance(get()) }

    // ViewModels
    viewModelOf(::AppConfigViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CodeRecordViewModel)
}

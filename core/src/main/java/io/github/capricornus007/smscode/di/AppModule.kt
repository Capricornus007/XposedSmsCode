package io.github.capricornus007.smscode.di

import io.github.capricornus007.smscode.data.db.AppDatabase
import io.github.capricornus007.smscode.ui.home.AppConfigViewModel
import io.github.capricornus007.smscode.ui.home.SettingsViewModel
import io.github.capricornus007.smscode.ui.record.CodeRecordViewModel
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

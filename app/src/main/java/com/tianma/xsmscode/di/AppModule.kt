package com.tianma.xsmscode.di

import com.tianma.xsmscode.data.db.AppDatabase
import com.tianma.xsmscode.ui.block.AppBlockViewModel
import com.tianma.xsmscode.ui.home.SettingsViewModel
import com.tianma.xsmscode.ui.record.CodeRecordViewModel
import com.tianma.xsmscode.ui.rule.edit.RuleEditViewModel
import com.tianma.xsmscode.ui.rule.list.RuleListViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // Database
    single { AppDatabase.getInstance(get()) }

    // ViewModels
    viewModelOf(::AppBlockViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CodeRecordViewModel)
    viewModelOf(::RuleListViewModel)
    viewModelOf(::RuleEditViewModel)
}

package com.tianma.xsmscode.ui.block

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.feature.store.EntityStoreManager
import com.tianma.xsmscode.feature.store.EntityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.persistentListOf
import androidx.compose.runtime.Immutable
import java.util.ArrayList
import java.util.Comparator

class AppBlockViewModel(application: Application) : AndroidViewModel(application) {

    private val _appsFlow = MutableStateFlow<ImmutableList<AppInfo>>(persistentListOf())
    val appsFlow: StateFlow<ImmutableList<AppInfo>> = _appsFlow.asStateFlow()

    private val _loadingFlow = MutableStateFlow(false)
    val loadingFlow: StateFlow<Boolean> = _loadingFlow.asStateFlow()

    private val _hideSystemAppsFlow = MutableStateFlow(true)
    val hideSystemAppsFlow: StateFlow<Boolean> = _hideSystemAppsFlow.asStateFlow()

    private val _hasChangesFlow = MutableStateFlow(false)
    val hasChangesFlow: StateFlow<Boolean> = _hasChangesFlow.asStateFlow()

    private val _sortOptionFlow = MutableStateFlow(SortOption.LABEL)
    val sortOptionFlow: StateFlow<SortOption> = _sortOptionFlow.asStateFlow()

    private val _isAscendingFlow = MutableStateFlow(true)
    val isAscendingFlow: StateFlow<Boolean> = _isAscendingFlow.asStateFlow()

    private val _events = MutableSharedFlow<AppBlockEvent>()
    val events: SharedFlow<AppBlockEvent> = _events.asSharedFlow()

    @Immutable
    sealed class AppBlockEvent {
        data class Error(val throwable: Throwable) : AppBlockEvent()
        object SaveSuccess : AppBlockEvent()
        object SaveFailed : AppBlockEvent()
        object ShowUsageStatsPermission : AppBlockEvent()
    }

    private var originalBlockedApps: ImmutableList<AppInfo> = persistentListOf()
    private var apps: ImmutableList<AppInfo> = persistentListOf()
    private var isLoadSucceed = false
    private val systemApps = HashSet<String>()

    private var filter = ""
    private var sortOption = SortOption.LABEL // Default sort by blocked status first? Actually user asked for simple sort. Let's keep blocked priority in comparator but allow sorting criteria.
    // User asked for: "Sort by App Name, Package Name, Usage Frequency". Let's default to LABEL.
    // Keeping "Blocked" at top is usually good UX, but user didn't explicitly ask for it to be removed.
    // Detailed requirement: "Sort: App Name, Package Name. Don't set 4. Click to ASC, click to DESC."
    // "Also add Usage Frequency".

    enum class SortOption {
        LABEL, PACKAGE, USAGE
    }

    var currentSortOption = SortOption.LABEL
        private set
    var isAscending = true
        private set

    private val usageStatsMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun refreshData() {
        if (isLoadSucceed) {
            if (_appsFlow.value.isEmpty() && apps.isNotEmpty()) {
                // If we have data but flow is empty (e.g. config change?), restore it.
                // But better re-apply filter/sort.
                applyFilterAndSort()
            }
            return
        }

        viewModelScope.launch {
            _loadingFlow.value = true
            try {
                // Load Usage Stats in background
                refreshUsageStats()

                val appList = withContext(Dispatchers.IO) {
                    val pm = getApplication<Application>().packageManager
                    originalBlockedApps = DBManager.get(getApplication()).queryAllBlockedAppsSuspend().toImmutableList()
                    
                    val installedApps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
                    val blockedPkgNames = originalBlockedApps.map { it.packageName }.toSet()

                    systemApps.clear()
                    installedApps.asSequence()
                        .map { app ->
                            val appInfo = AppInfoHelper.getAppInfo(pm, app)
                            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                            if (isSystemApp) {
                                systemApps.add(appInfo.packageName)
                            }
                            if (blockedPkgNames.contains(appInfo.packageName)) {
                                appInfo.copy(blocked = true)
                            } else {
                                appInfo
                            }
                        }
                        .toImmutableList()
                }
                
                apps = appList
                isLoadSucceed = true
                updateHasChanges()
                applyFilterAndSort()
                _loadingFlow.value = false
            } catch (t: Throwable) {
                XLog.e("", t)
                _loadingFlow.value = false
                viewModelScope.launch { _events.emit(AppBlockEvent.Error(t)) }
                isLoadSucceed = false
            }
        }
    }

    private fun refreshUsageStats() {
        try {
            val context = getApplication<Application>()
            val usageStatsManager = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1000 * 3600 * 24 * 30L // Last 30 days
            
            // We use queryUsageStats to get detailed stats, or queryAndAggregateUsageStats
            val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            usageStatsMap.clear()
            if (stats != null) {
                for ((pkg, usage) in stats) {
                    usageStatsMap[pkg] = usage.totalTimeInForeground
                }
            }
        } catch (e: Exception) {
            XLog.e("Failed to load usage stats", e)
        }
    }

    fun doFilter(newFilter: String) {
        filter = newFilter.lowercase()
        applyFilterAndSort()
    }

    fun setSortOption(option: SortOption) {
        if (option == SortOption.USAGE) {
            if (!hasUsageStatsPermission()) {
                viewModelScope.launch { _events.emit(AppBlockEvent.ShowUsageStatsPermission) }
                return
            }
        }

        if (currentSortOption != option) {
            currentSortOption = option
            isAscending = option != SortOption.USAGE
            _sortOptionFlow.value = currentSortOption
            _isAscendingFlow.value = isAscending
        }
        applyFilterAndSort()
    }

    fun setAscending(ascending: Boolean) {
        isAscending = ascending
        _isAscendingFlow.value = isAscending
        applyFilterAndSort()
    }

    fun setHideSystemApps(hide: Boolean) {
        _hideSystemAppsFlow.value = hide
        applyFilterAndSort()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getApplication<Application>().getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            getApplication<Application>().packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun applyFilterAndSort() {
        viewModelScope.launch {
            val filteredList = withContext(Dispatchers.Default) {
                apps.asSequence()
                    .filter { appInfo ->
                        if (_hideSystemAppsFlow.value && systemApps.contains(appInfo.packageName)) {
                            return@filter false
                        }
                        if (filter.isEmpty()) true else {
                            val lowerLabel = appInfo.label?.lowercase() ?: ""
                            val lowerPkg = appInfo.packageName.lowercase()
                            lowerLabel.contains(filter) || lowerPkg.contains(filter)
                        }
                    }
                    .sortedWith(mComparator)
                    .toImmutableList()
            }
            _appsFlow.value = filteredList
        }
    }

    fun doItemClicked(item: AppInfo) {
        val updatedApps = apps.map { appInfo ->
            if (appInfo.packageName == item.packageName) {
                appInfo.copy(blocked = !appInfo.blocked)
            } else {
                appInfo
            }
        }.toImmutableList()
        apps = updatedApps
        updateHasChanges()
        // Re-apply sort/filter to keep consistency (e.g. if sorting by blocked status)
        // Optimization: simply update the flow with new blocked state, but we need to find it in the current filtered list.
        applyFilterAndSort()
    }

    fun saveData() {
        if (apps.isEmpty()) {
            viewModelScope.launch { _events.emit(AppBlockEvent.SaveSuccess) }
            return
        }

        val blockedApps = apps.filter { it.blocked }

        // Simple check might fail if order changed, better check contents logic or just save.
        // Let's just save to be safe.

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dbManager = DBManager.get(getApplication())
                    dbManager.deleteAllSuspend(AppInfo::class.java)
                    dbManager.insertOrReplaceInTxSuspend(AppInfo::class.java, blockedApps)
                    
                    EntityStoreManager.storeEntitiesToFile(
                        getApplication(), EntityType.BLOCKED_APP, blockedApps, AppInfo::class.java
                    )
                }
                // Update original checks
                originalBlockedApps = blockedApps.toImmutableList()
                updateHasChanges()
                _events.emit(AppBlockEvent.SaveSuccess)
            } catch (t: Throwable) {
                _events.emit(AppBlockEvent.SaveFailed)
            }
        }
    }

    private fun updateHasChanges() {
        val current = apps.asSequence().filter { it.blocked }.map { it.packageName }.toSet()
        val original = originalBlockedApps.asSequence().map { it.packageName }.toSet()
        _hasChangesFlow.value = current != original
    }

    private val mComparator = Comparator<AppInfo> { o1, o2 ->
        // Always blocked on top? The user didn't specify, but it's "App Block" feature.
        // Let's keep blocked on top for convenience, then apply sort option.
        if (o1.blocked != o2.blocked) {
            return@Comparator if (o1.blocked) -1 else 1
        }

        val result = when (currentSortOption) {
            SortOption.LABEL -> compareString(o1.label, o2.label)
            SortOption.PACKAGE -> compareString(o1.packageName, o2.packageName)
            SortOption.USAGE -> {
                val u1 = usageStatsMap[o1.packageName] ?: 0L
                val u2 = usageStatsMap[o2.packageName] ?: 0L
                u1.compareTo(u2)
            }
        }
        
        if (isAscending) result else -result
    }

    private fun compareString(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1
        return s1.compareTo(s2, ignoreCase = true)
    }
}

package com.milki.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

data class AppInfo(
    val name: String,
    val packageName: String,
    val launchIntent: Intent?
) {
    // Cache lowercase for faster filtering
    val nameLower: String by lazy { name.lowercase() }
    val packageLower: String by lazy { packageName.lowercase() }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val recentAppsKey = stringPreferencesKey("recent_apps")
    
    // Limit parallelism to avoid memory spikes with 150+ apps
    private val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)
    
    val installedApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    val recentApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    
    init {
        loadInstalledApps()
        loadRecentApps()
    }
    
    private fun loadInstalledApps() {
        viewModelScope.launch {
            withContext(limitedDispatcher) {
                val pm = getApplication<Application>().packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                
                // Load app info with controlled parallelism to avoid memory spikes
                // Process in chunks of 8 instead of launching 150+ coroutines at once
                val apps = resolveInfos.chunked(8).flatMap { chunk ->
                    chunk.map { resolveInfo ->
                        async {
                            AppInfo(
                                name = resolveInfo.loadLabel(pm).toString(),
                                packageName = resolveInfo.activityInfo.packageName,
                                launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                            )
                        }
                    }.awaitAll()
                }.sortedBy { it.nameLower }
                
                // Update UI state on main thread
                withContext(Dispatchers.Main) {
                    installedApps.clear()
                    installedApps.addAll(apps)
                }
            }
        }
    }
    
    private fun loadRecentApps() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val recentPackages = getApplication<Application>().dataStore.data.map { preferences ->
                    preferences[recentAppsKey] ?: ""
                }.first().split(",").filter { it.isNotEmpty() }
                
                val pm = getApplication<Application>().packageManager
                
                val apps = recentPackages.mapNotNull { packageName ->
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        AppInfo(
                            name = pm.getApplicationLabel(appInfo).toString(),
                            packageName = packageName,
                            launchIntent = pm.getLaunchIntentForPackage(packageName)
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
                // Update UI state on main thread
                withContext(Dispatchers.Main) {
                    recentApps.clear()
                    recentApps.addAll(apps)
                }
            }
        }
    }
    
    fun saveRecentApp(packageName: String) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { preferences ->
                val current = preferences[recentAppsKey] ?: ""
                val recentPackages = current.split(",")
                    .filter { it.isNotEmpty() }
                    .toMutableList()
                
                recentPackages.remove(packageName)
                recentPackages.add(0, packageName)
                
                preferences[recentAppsKey] = recentPackages.take(5).joinToString(",")
            }
            loadRecentApps()
        }
    }
}

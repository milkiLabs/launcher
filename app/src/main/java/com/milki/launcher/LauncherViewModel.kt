package com.milki.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

// Helper extension to convert Drawable to ImageBitmap efficiently
fun Drawable.toImageBitmap(size: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    this.setBounds(0, 0, canvas.width, canvas.height)
    this.draw(canvas)
    return bitmap.asImageBitmap()
}

data class AppInfo(
    val name: String,
    val packageName: String,
    val launchIntent: Intent?,
    val icon: ImageBitmap?
) {
    // Cache lowercase for faster filtering
    val nameLower: String by lazy { name.lowercase() }
    val packageLower: String by lazy { packageName.lowercase() }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val recentAppsKey = stringPreferencesKey("recent_apps")
    
    val installedApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    val recentApps: SnapshotStateList<AppInfo> = mutableStateListOf()
    
    init {
        loadInstalledApps()
        loadRecentApps()
    }
    
    private fun loadInstalledApps() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                
                // Calculate icon size in pixels (48dp)
                val iconSizePx = (getApplication<Application>().resources.displayMetrics.density * 48).toInt()
                
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                
                // Load icons in parallel for faster startup
                val apps = resolveInfos.map { resolveInfo ->
                    async {
                        val rawDrawable = resolveInfo.loadIcon(pm)
                        AppInfo(
                            name = resolveInfo.loadLabel(pm).toString(),
                            packageName = resolveInfo.activityInfo.packageName,
                            launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName),
                            // Convert to ImageBitmap once, in background
                            icon = rawDrawable.toImageBitmap(iconSizePx)
                        )
                    }
                }.awaitAll().sortedBy { it.nameLower }
                
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
                val iconSizePx = (getApplication<Application>().resources.displayMetrics.density * 48).toInt()
                
                val apps = recentPackages.mapNotNull { packageName ->
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val rawIcon = pm.getApplicationIcon(packageName)
                        AppInfo(
                            name = pm.getApplicationLabel(appInfo).toString(),
                            packageName = packageName,
                            launchIntent = pm.getLaunchIntentForPackage(packageName),
                            // Convert to ImageBitmap once, in background
                            icon = rawIcon.toImageBitmap(iconSizePx)
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

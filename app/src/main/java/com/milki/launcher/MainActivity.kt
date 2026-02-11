package com.milki.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.milki.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

data class AppInfo(
    val name: String,
    val packageName: String,
    val launchIntent: Intent?,
    val icon: Drawable?
)

class MainActivity : ComponentActivity() {
    private var showSearch by mutableStateOf(false)
    private val installedApps = mutableStateListOf<AppInfo>()
    private val recentApps = mutableStateListOf<AppInfo>()
    
    private val recentAppsKey = stringPreferencesKey("recent_apps")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadInstalledApps()
        
        lifecycleScope.launch {
            loadRecentApps()
        }
        
        setContent {
            LauncherTheme {
                LauncherScreen(
                    showSearch = showSearch,
                    onShowSearch = { showSearch = true },
                    onHideSearch = { showSearch = false },
                    installedApps = installedApps,
                    recentApps = recentApps,
                    onLaunchApp = { launchApp(it) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            showSearch = !showSearch
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val apps = pm.queryIntentActivities(mainIntent, 0)
            .map { resolveInfo ->
                AppInfo(
                    name = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName),
                    icon = resolveInfo.loadIcon(pm)
                )
            }
            .sortedBy { it.name.lowercase() }
        
        installedApps.clear()
        installedApps.addAll(apps)
    }
    
    private suspend fun loadRecentApps() {
        val recentPackages = dataStore.data.map { preferences ->
            preferences[recentAppsKey] ?: ""
        }.first().split(",").filter { it.isNotEmpty() }
        
        val pm = packageManager
        val apps = recentPackages.mapNotNull { packageName ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                AppInfo(
                    name = pm.getApplicationLabel(appInfo).toString(),
                    packageName = packageName,
                    launchIntent = pm.getLaunchIntentForPackage(packageName),
                    icon = pm.getApplicationIcon(packageName)
                )
            } catch (e: Exception) {
                null
            }
        }
        
        recentApps.clear()
        recentApps.addAll(apps)
    }
    
    private fun saveRecentApp(packageName: String) {
        lifecycleScope.launch {
            dataStore.edit { preferences ->
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

    private fun launchApp(appInfo: AppInfo) {
        appInfo.launchIntent?.let { startActivity(it) }
        saveRecentApp(appInfo.packageName)
        showSearch = false
    }
}

@Composable
fun LauncherScreen(
    showSearch: Boolean,
    onShowSearch: () -> Unit,
    onHideSearch: () -> Unit,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>,
    onLaunchApp: (AppInfo) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onShowSearch() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Tap to search",
            color = Color.White.copy(alpha = 0.3f),
            style = MaterialTheme.typography.bodyLarge
        )
    }

    if (showSearch) {
        AppSearchDialog(
            installedApps = installedApps,
            recentApps = recentApps,
            onDismiss = onHideSearch,
            onLaunchApp = onLaunchApp
        )
    }
}

@Composable
fun AppSearchDialog(
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Filtering: Search by Name OR Package Name with priority ranking
    val filteredApps = remember(searchQuery, installedApps, recentApps) {
        if (searchQuery.isBlank()) {
            recentApps
        } else {
            val query = searchQuery.trim()
            val exactMatches = mutableListOf<AppInfo>()
            val startsWithMatches = mutableListOf<AppInfo>()
            val containsMatches = mutableListOf<AppInfo>()
            
            installedApps.forEach { app ->
                val nameLower = app.name.lowercase()
                val queryLower = query.lowercase()
                val packageLower = app.packageName.lowercase()
                
                when {
                    // Exact match (name or package)
                    nameLower == queryLower || packageLower == queryLower -> {
                        exactMatches.add(app)
                    }
                    // Starts with (name or package)
                    nameLower.startsWith(queryLower) || packageLower.startsWith(queryLower) -> {
                        startsWithMatches.add(app)
                    }
                    // Contains (name or package)
                    nameLower.contains(queryLower) || packageLower.contains(queryLower) -> {
                        containsMatches.add(app)
                    }
                }
            }
            
            exactMatches + startsWithMatches + containsMatches
        }
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false // Helps with IME (Keyboard) padding
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f)
                .imePadding(), // 3. Push content up when keyboard opens

            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search apps...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            filteredApps.firstOrNull()?.let { onLaunchApp(it) }
                        }
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    }
                )


                if (filteredApps.isEmpty()) {
                    // 5. Empty State
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No recent apps" else "No apps found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredApps) { app ->
                            AppListItem(
                                appInfo = app,
                                onClick = { onLaunchApp(app) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Auto-focus the keyboard when dialog opens
    LaunchedEffect(Unit) {
        // A slight delay is sometimes needed for the Dialog window to settle
        kotlinx.coroutines.delay(10)
        focusRequester.requestFocus()
    }
}

@Composable
fun AppListItem(
    appInfo: AppInfo,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            appInfo.icon?.let { drawable ->
                Image(
                    bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
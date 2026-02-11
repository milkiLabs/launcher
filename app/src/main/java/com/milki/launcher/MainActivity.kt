package com.milki.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.milki.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

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

class MainActivity : ComponentActivity() {
    private var showSearch by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private val installedApps = mutableStateListOf<AppInfo>()
    private val recentApps = mutableStateListOf<AppInfo>()
    
    private val recentAppsKey = stringPreferencesKey("recent_apps")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load apps in background to avoid blocking UI
        lifecycleScope.launch {
            loadInstalledApps()
            loadRecentApps()
        }
        
        setContent {
            LauncherTheme {
                LauncherScreen(
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onShowSearch = { showSearch = true },
                    onHideSearch = { 
                        showSearch = false
                        searchQuery = ""
                    },
                    onSearchQueryChange = { searchQuery = it },
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
            when {
                !showSearch -> showSearch = true
                searchQuery.isNotEmpty() -> searchQuery = ""
                else -> showSearch = false
            }
        }
    }

    private suspend fun loadInstalledApps() = withContext(Dispatchers.IO) {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        // Calculate icon size in pixels (48dp)
        val iconSizePx = (resources.displayMetrics.density * 48).toInt()
        
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
    
    private suspend fun loadRecentApps() = withContext(Dispatchers.IO) {
        val recentPackages = dataStore.data.map { preferences ->
            preferences[recentAppsKey] ?: ""
        }.first().split(",").filter { it.isNotEmpty() }
        
        val pm = packageManager
        val iconSizePx = (resources.displayMetrics.density * 48).toInt()
        
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
        searchQuery = ""
    }
}

@Composable
fun LauncherScreen(
    showSearch: Boolean,
    searchQuery: String,
    onShowSearch: () -> Unit,
    onHideSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
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
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            installedApps = installedApps,
            recentApps = recentApps,
            onDismiss = onHideSearch,
            onLaunchApp = onLaunchApp
        )
    }
}

@Composable
fun AppSearchDialog(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    // Use remember with proper dependencies for filtering
    val filteredApps = remember(searchQuery, installedApps, recentApps) {
        if (searchQuery.isBlank()) {
            recentApps
        } else {
            val queryLower = searchQuery.trim().lowercase()
            val exactMatches = mutableListOf<AppInfo>()
            val startsWithMatches = mutableListOf<AppInfo>()
            val containsMatches = mutableListOf<AppInfo>()
            
            installedApps.forEach { app ->
                when {
                    // Exact match (name or package) - use cached lowercase
                    app.nameLower == queryLower || app.packageLower == queryLower -> {
                        exactMatches.add(app)
                    }
                    // Starts with (name or package)
                    app.nameLower.startsWith(queryLower) || app.packageLower.startsWith(queryLower) -> {
                        startsWithMatches.add(app)
                    }
                    // Contains (name or package)
                    app.nameLower.contains(queryLower) || app.packageLower.contains(queryLower) -> {
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
                    onValueChange = onSearchQueryChange,
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
                            IconButton(onClick = { onSearchQueryChange("") }) {
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
                        items(
                            items = filteredApps,
                            key = { app -> app.packageName },
                            contentType = { "app_item" }
                        ) { app ->
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
            appInfo.icon?.let { imageBitmap ->
                Image(
                    bitmap = imageBitmap,
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
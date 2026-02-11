package com.milki.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.milki.launcher.ui.theme.LauncherTheme

data class AppInfo(
    val name: String,
    val packageName: String,
    val launchIntent: Intent?
)

class MainActivity : ComponentActivity() {
    private var showSearch by mutableStateOf(false)
    private val installedApps = mutableStateListOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadInstalledApps()
        
        setContent {
            LauncherTheme {
                LauncherScreen(
                    showSearch = showSearch,
                    onShowSearch = { showSearch = true },
                    onHideSearch = { showSearch = false },
                    installedApps = installedApps,
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
                    launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                )
            }
            .sortedBy { it.name.lowercase() }
        
        installedApps.clear()
        installedApps.addAll(apps)
    }

    private fun launchApp(appInfo: AppInfo) {
        appInfo.launchIntent?.let { startActivity(it) }
        showSearch = false
    }
}

@Composable
fun LauncherScreen(
    showSearch: Boolean,
    onShowSearch: () -> Unit,
    onHideSearch: () -> Unit,
    installedApps: List<AppInfo>,
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
            onDismiss = onHideSearch,
            onLaunchApp = onLaunchApp
        )
    }
}

@Composable
fun AppSearchDialog(
    installedApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    
    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isEmpty()) {
            installedApps
        } else {
            installedApps.filter { 
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
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
                    colors = OutlinedTextFieldDefaults.colors()
                )

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

    LaunchedEffect(Unit) {
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
        Text(
            text = appInfo.name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
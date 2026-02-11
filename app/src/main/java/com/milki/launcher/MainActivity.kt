package com.milki.launcher

import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.milki.launcher.ui.theme.LauncherTheme

class MainActivity : ComponentActivity() {
    private var showSearch by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val viewModel: LauncherViewModel = viewModel()
            
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
                    installedApps = viewModel.installedApps,
                    recentApps = viewModel.recentApps,
                    onLaunchApp = { appInfo ->
                        appInfo.launchIntent?.let { startActivity(it) }
                        viewModel.saveRecentApp(appInfo.packageName)
                        showSearch = false
                        searchQuery = ""
                    }
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
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .imePadding()
                .navigationBarsPadding()
                .statusBarsPadding(),
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
            // Lazy load icon using Coil with LRU cache
            val painter = rememberAsyncImagePainter(
                model = AppIconRequest(appInfo.packageName)
            )
            
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = appInfo.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
# MainActivity.kt - Detailed Documentation

## Overview

`MainActivity.kt` is the main entry point and UI component of the Milki Launcher. It's responsible for:

- Displaying the home screen
- Handling user interactions (taps, search)
- Managing the search dialog
- Launching apps when selected
- Coordinating with the ViewModel for data

This file contains both the Activity class and several Composable functions that define the UI.

---

## Table of Contents

1. [Class Structure](#class-structure)
2. [MainActivity Class](#mainactivity-class)
3. [State Variables](#state-variables)
4. [onCreate Method](#oncreate-method)
5. [onNewIntent Method](#onnewintent-method)
6. [LauncherScreen Composable](#launcherscreen-composable)
7. [AppSearchDialog Composable](#appsearchdialog-composable)
8. [AppListItem Composable](#applistitem-composable)
9. [How It All Works Together](#how-it-all-works-together)

---

## Class Structure

```kotlin
MainActivity.kt
├── MainActivity class (extends ComponentActivity)
│   ├── State variables (showSearch, searchQuery)
│   ├── onCreate() - Initializes the UI
│   └── onNewIntent() - Handles launcher button press
│
├── LauncherScreen() - Main UI composable
├── AppSearchDialog() - Search dialog composable
└── AppListItem() - Individual app item composable
```

---

## State Variables

showSearch, searchQuery

- Why are these in the Activity?
  - These state variables need to survive configuration changes and be accessible to both `onCreate()` and `onNewIntent()`. By declaring them at the class level:
  - They persist as long as the Activity exists

---

## onCreate Method

This is where we call our main UI composable and pass all the data and callbacks it needs.

### Understanding the Parameters

- `onLaunchApp: (AppInfo) -> Unit` - Called when user selects an app

---

## onNewIntent Method

- `onNewIntent()` is called when the Activity is already running and receives a new Intent. This happens when:
  - User presses the home button
  - User presses the launcher button
  - Another app tries to open your launcher

### launchMode="singleTask"

This means only one instance of MainActivity can exist.
When the user presses home, instead of creating a new Activity, Android brings the existing one to the front and calls `onNewIntent()`.

### The Three-State Logic

**First Press (Home Button)**:

- `showSearch` is `false`
- Opens search dialog

**Second Press**:

- `showSearch` is `true`
- `searchQuery` might have text
- Clears the search text

**Third Press**:

- `showSearch` is `true`
- `searchQuery` is empty
- Closes search dialog

This creates a natural cycle: Open → Clear → Close

---

## LauncherScreen Composable

```kotlin
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
```

### The @Composable Annotation

`@Composable` marks a function as a Compose UI component. Composable functions:

- Can call other composables
- Should be side-effect free (mostly)
- Run on the UI thread
- Automatically recompose when state changes

### The Home Screen Design

```kotlin
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
```

**Box Layout**:

- `fillMaxSize()`: Takes up entire screen
- `background(Color.Black)`: Pure black background
- `clickable { onShowSearch() }`: Entire screen is tappable
- `contentAlignment = Alignment.Center`: Centers child content

**Why a Box?**
A Box is the simplest layout - it stacks children on top of each other. Here we only have one child (Text), so Box is perfect.

**The Text**:

- Displays "Tap to search"
- White color with 30% opacity (subtle hint)
- Uses MaterialTheme typography for consistent styling

### Conditional Display

```kotlin
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
```

This uses a simple Kotlin `if` statement to conditionally show the search dialog. This is one of Compose's superpowers - UI is just Kotlin code!

---

## AppSearchDialog Composable

This is the most complex composable in the project. Let's break it down:

```kotlin
@Composable
fun AppSearchDialog(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    installedApps: List<AppInfo>,
    recentApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit
)
```

### Focus Requester

```kotlin
val focusRequester = remember { FocusRequester() }
```

**What is it?** A FocusRequester is used to programmatically control focus in Compose.

**Why we need it**: We want the keyboard to automatically appear when the dialog opens. To do this, we need to request focus on the text field programmatically.

**remember**: The `remember` function creates a value that survives recompositions. Without it, a new FocusRequester would be created every time the UI updates.

### Filtering Logic

```kotlin
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
                app.nameLower == queryLower || app.packageLower == queryLower -> {
                    exactMatches.add(app)
                }
                app.nameLower.startsWith(queryLower) || app.packageLower.startsWith(queryLower) -> {
                    startsWithMatches.add(app)
                }
                app.nameLower.contains(queryLower) || app.packageLower.contains(queryLower) -> {
                    containsMatches.add(app)
                }
            }
        }

        exactMatches + startsWithMatches + containsMatches
    }
}
```

#### remember() with Keys

```kotlin
val filteredApps = remember(searchQuery, installedApps, recentApps) { ... }
```

The `remember` function takes "keys" as parameters. It only recomputes the value when one of the keys changes.

**Keys**:

- `searchQuery`: When user types
- `installedApps`: When apps are loaded/updated
- `recentApps`: When recent apps change

**Why this matters**: Without `remember`, the filtering would run on every keystroke AND every recomposition (which could be hundreds of times per second). With `remember`, it only runs when the keys change.

#### Three-Tier Matching System

The search uses a sophisticated three-tier system:

**Tier 1: Exact Matches**

```kotlin
app.nameLower == queryLower || app.packageLower == queryLower
```

- App name exactly equals search query
- OR package name exactly equals search query
- Highest priority

**Tier 2: Starts With**

```kotlin
app.nameLower.startsWith(queryLower) || app.packageLower.startsWith(queryLower)
```

- App name starts with search query
- OR package name starts with search query
- Medium priority

**Tier 3: Contains**

```kotlin
app.nameLower.contains(queryLower) || app.packageLower.contains(queryLower)
```

- App name contains search query anywhere
- OR package name contains search query anywhere
- Lowest priority

**Combining Results**:

```kotlin
exactMatches + startsWithMatches + containsMatches
```

This concatenates the lists in priority order, so exact matches appear first.

#### Example Search

If you type "mes":

1. **Exact matches**: None (unless an app is literally named "mes")
2. **Starts with**: "Messages", "Messenger", "Mestastic"
3. **Contains**: "WhatsApp Messenger" (contains "mes"), "Wireless" (if any)

Final order: Messages, Messenger, Mestastic, WhatsApp Messenger

### Back Handler

```kotlin
BackHandler { onDismiss() }
```

**What it does**: Intercepts the Android back button press.

**Without this**: Pressing back would close the entire launcher (going to the previous app).

**With this**: Pressing back dismisses the search dialog instead.

### Dialog Component

```kotlin
Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = true
    )
) {
```

**Dialog**: A Compose component that displays content in a modal dialog.

**onDismissRequest**: Called when user taps outside the dialog or presses back.

**DialogProperties**:

- `usePlatformDefaultWidth = false`: Allows custom width (we use 90% of screen)
- `decorFitsSystemWindows = true`: Respects system bars (status bar, navigation bar)

### Surface Layout

```kotlin
Surface(
    modifier = Modifier
        .fillMaxWidth(0.9f)      // 90% of screen width
        .fillMaxHeight(0.8f)     // 80% of screen height
        .imePadding()            // Padding for keyboard
        .navigationBarsPadding() // Padding for navigation bar
        .statusBarsPadding(),    // Padding for status bar
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 8.dp
) {
```

**Surface**: A Material container that draws a background and handles elevation.

**Modifier Chain**:

1. `fillMaxWidth(0.9f)`: Takes up 90% of parent's width
2. `fillMaxHeight(0.8f)`: Takes up 80% of parent's height
3. `imePadding()`: Adds padding when keyboard opens
4. `navigationBarsPadding()`: Avoids navigation bar (gesture area)
5. `statusBarsPadding()`: Avoids status bar (clock, battery)

**RoundedCornerShape(16.dp)**: Rounds the corners with 16dp radius.

**tonalElevation = 8.dp**: Adds a subtle shadow/elevation effect.

### Search Text Field

```kotlin
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
    keyboardOptions = KeyboardOptions(
        imeAction = ImeAction.Done
    ),
    keyboardActions = KeyboardActions(
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
```

**OutlinedTextField**: A Material Design 3 text input field with an outline.

**Parameters Explained**:

- `value`: Current text value (controlled component pattern)
- `onValueChange`: Called when text changes
- `modifier`: Layout and behavior modifications
- `placeholder`: Hint text shown when empty
- `singleLine = true`: Prevents multiline input

**Keyboard Configuration**:

```kotlin
keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
```

- Shows "Done" button on keyboard instead of "Enter"

```kotlin
keyboardActions = KeyboardActions(
    onDone = {
        filteredApps.firstOrNull()?.let { onLaunchApp(it) }
    }
)
```

- When user presses "Done", launches the first matching app
- This allows quick launching without scrolling

**Clear Button**:

```kotlin
trailingIcon = {
    if (searchQuery.isNotEmpty()) {
        IconButton(onClick = { onSearchQueryChange("") }) {
            Icon(Icons.Default.Close, contentDescription = "Clear search")
        }
    }
}
```

- Shows an X button when there's text
- Clears the search when tapped

### Empty State

```kotlin
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
}
```

**What it shows**:

- If search is empty: "No recent apps" (user hasn't launched any apps yet)
- If searching: "No apps found" (no matches for their search)

### App List

```kotlin
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
```

**LazyColumn**: A vertically scrolling list that only composes visible items.

**Why LazyColumn?**

- Efficient for long lists (only renders what's on screen)
- Built-in recycling of views
- Smooth scrolling performance

**items() Parameters**:

- `items`: The list to display
- `key`: Unique identifier for each item (enables animations and efficient updates)
- `contentType`: Optimization hint for Compose

### Auto-Focus Effect

```kotlin
LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(10)
    focusRequester.requestFocus()
}
```

**LaunchedEffect**: Runs a coroutine when the composable enters composition.

**Unit as key**: Only runs once (when dialog first opens).

**delay(10)**: Small delay to ensure the UI is ready.

**requestFocus()**: Opens the keyboard and focuses the text field.

---

## AppListItem Composable

```kotlin
@Composable
fun AppListItem(
    appInfo: AppInfo,
    onClick: () -> Unit
) {
```

### Surface Container

```kotlin
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick),
    color = Color.Transparent
) {
```

**Surface**: Provides Material styling and ripple effects on click.

**fillMaxWidth()**: Item spans full width of list.

**clickable(onClick = onClick)**: Makes the entire row tappable.

### Row Layout

```kotlin
Row(
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
```

**Row**: Lays out children horizontally (side by side).

**Padding**: 16dp left/right, 12dp top/bottom for comfortable touch targets.

**verticalAlignment = Alignment.CenterVertically**: Centers items vertically.

### Icon Loading

```kotlin
val painter = rememberAsyncImagePainter(
    model = AppIconRequest(appInfo.packageName)
)

Image(
    painter = painter,
    contentDescription = null,
    modifier = Modifier.size(40.dp)
)
```

**rememberAsyncImagePainter**: Coil's Compose integration for async image loading.

**AppIconRequest**: Custom data class that tells our custom Fetcher which app icon to load.

**contentDescription = null**: Icons are decorative, not informative (app name is shown).

**size(40.dp)**: Standard app icon size in lists.

### Spacer

```kotlin
Spacer(modifier = Modifier.width(12.dp))
```

Adds 12dp of horizontal space between icon and text.

### App Name

```kotlin
Text(
    text = appInfo.name,
    style = MaterialTheme.typography.bodyLarge
)
```

Displays the app's display name using Material typography.

---

## How It All Works Together

### Complete User Flow

**1. User Opens Launcher**

```
System creates MainActivity
    ↓
onCreate() is called
    ↓
ViewModel is created/retrieved
    ↓
LauncherScreen() is composed
    ↓
Shows black screen with "Tap to search"
```

**2. User Taps Screen**

```
User taps anywhere on screen
    ↓
clickable { onShowSearch() } triggers
    ↓
Activity sets showSearch = true
    ↓
Compose recomposes
    ↓
AppSearchDialog appears
    ↓
Keyboard opens automatically
```

**3. User Types "you"**

```
User types "y"
    ↓
OutlinedTextField calls onValueChange
    ↓
Activity sets searchQuery = "y"
    ↓
Compose recomposes AppSearchDialog
    ↓
remember() sees searchQuery changed
    ↓
Filtering logic runs
    ↓
LazyColumn displays filtered apps
    ↓
User types "o"
    ↓
searchQuery = "yo"
    ↓
Filtering runs again
    ↓
List updates
```

**4. User Taps an App**

```
User taps "YouTube" item
    ↓
AppListItem's onClick triggers
    ↓
onLaunchApp callback called
    ↓
startActivity(launchIntent) opens YouTube
    ↓
viewModel.saveRecentApp() saves to DataStore
    ↓
Activity sets showSearch = false, searchQuery = ""
    ↓
Search dialog closes
```

**5. User Returns to Launcher**

```
User presses home button
    ↓
onNewIntent() called with ACTION_MAIN
    ↓
If search closed → opens it
    ↓
If search open with text → clears text
    ↓
If search open empty → closes it
```

---

## Key Concepts Summary

### State Hoisting

State is stored in the Activity and passed down to composables:

- **Activity**: Owns the state
- **LauncherScreen**: Receives state and callbacks
- **AppSearchDialog**: Receives state, sends events back

This pattern makes components reusable and testable.

### Recomposition

Compose intelligently redraws only what changed:

- Typing in search → Only TextField and List recompose
- Launching app → Dialog closes, main screen stays
- Rotating device → ViewModel survives, UI rebuilds

### Coroutines in Compose

- `LaunchedEffect`: Run coroutines tied to composable lifecycle
- `viewModelScope`: Run coroutines tied to ViewModel lifecycle
- `Dispatchers.IO`: Run on background thread
- `Dispatchers.Main`: Run on UI thread

### Performance Optimizations

1. **remember()**: Cache expensive computations
2. **LazyColumn**: Only render visible items
3. **keys in items()**: Efficient list updates
4. **pre-computed lowercase**: Avoid repeated string operations
5. **chunked parallelism**: Control memory usage

---

## Exercises for Learning

1. **Add App Package Name**: Modify AppListItem to also show the package name below the app name

2. **Change Search Hint**: Make the search hint dynamic (e.g., "Search 47 apps...")

3. **Add Highlighting**: Highlight the matching part of app names in search results

4. **Empty State Icon**: Add an icon to the empty state (magnifying glass or sad face)

5. **Long Press Menu**: Add a long-press gesture to show app info/uninstall options

---

## Troubleshooting

### Dialog Not Showing

- Check that `showSearch` is being set to `true`
- Verify Dialog composable is inside the `if (showSearch)` block
- Check for crashes in logcat

### Keyboard Not Opening

- Ensure `focusRequester.requestFocus()` is called
- Verify text field has `focusRequester(focusRequester)` modifier
- Check that dialog is fully composed before requesting focus

### Search Not Filtering

- Verify `remember()` has correct keys
- Check that `onSearchQueryChange` is updating `searchQuery`
- Ensure filtering logic uses lowercase consistently

### Apps Not Launching

- Check that `launchIntent` is not null
- Verify correct permissions in manifest
- Look for ActivityNotFoundException in logcat

---

This documentation should give you a complete understanding of how MainActivity.kt works. Each section builds on the previous one, so if something is unclear, try reading from the beginning again or look at the actual code while reading.

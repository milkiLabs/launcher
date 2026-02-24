# MainActivity Architecture

This document explains the architecture of MainActivity and its handler classes.

## Overview

MainActivity has been refactored from a ~500 line monolithic class into a lean orchestrator that delegates to specialized handlers. This follows the **Single Responsibility Principle** - each class has one clear purpose.

## File Structure

```
app/src/main/java/com/milki/launcher/
├── MainActivity.kt          (~200 lines) - Lifecycle, UI composition, home button handling
└── handlers/
    ├── PermissionHandler.kt (~230 lines) - All permission requests and state
    └── ActionHandler.kt     (~200 lines) - All search action execution
```

## Class Responsibilities

### MainActivity

**What it does:**
- UI composition (setting up Compose content)
- Lifecycle management (onCreate, onResume, onStop)
- Home button detection and search toggling
- Delegating to handlers

**What it does NOT do:**
- Permission requests (delegated to PermissionHandler)
- Action execution (delegated to ActionHandler)
- Business logic (in ViewModel)

### PermissionHandler

**What it does:**
- Register permission launchers (contacts, files, manage storage)
- Check permission states
- Request permissions from user
- Update ViewModel with permission states

**Why separate it?**
- Permission logic is complex (different for Android 11+)
- Can be reused by Settings Activity
- Easier to test independently
- Settings can affect permission requirements

### ActionHandler

**What it does:**
- Launch apps
- Open URLs and web searches
- Open YouTube searches (with app preference)
- Make phone calls
- Open files

**Why separate it?**
- Action execution will be customizable via settings
- Browser choice, YouTube preference, etc. can be injected
- Easier to test independently
- Could be extended for custom actions

## Data Flow

```
User Action
    │
    ▼
┌─────────────────┐
│  LauncherScreen │  (Compose UI)
└────────┬────────┘
         │ onResultClick()
         ▼
┌─────────────────┐
│ SearchViewModel │  (State + Business Logic)
└────────┬────────┘
         │ emit SearchAction
         ▼
┌─────────────────┐
│  MainActivity   │  (Orchestrator)
└────────┬────────┘
         │ dispatch
         ▼
┌─────────────────────────────────────┐
│ PermissionHandler │ ActionHandler   │  (Execution)
└─────────────────────────────────────┘
         │
         ▼
    System/App (Intent)
```

## Home Button Detection

The home button detection uses lifecycle state tracking, not intent flags:

1. `onStop()` sets `wasAlreadyOnHomescreen = false` (user left home screen)
2. User presses home → `onNewIntent()` fires BEFORE `onResume()`
3. At this point, flag is still `false` → We know user is returning from an app
4. `onResume()` sets `wasAlreadyOnHomescreen = true`
5. User presses home again → `onNewIntent()` fires
6. Flag is `true` → We know user was already on home screen

**Why not use `FLAG_ACTIVITY_BROUGHT_TO_FRONT`?**

That flag has the opposite meaning of what you'd expect:
- Set when activity is brought to front from **background** (returning from app)
- NOT set when activity is already in foreground (pressing home while on home)

## Future Extensibility (Settings Integration)

### ActionHandler Customization

```kotlin
// Future: Inject settings repository
class ActionHandler(
    private val context: Context,
    private val searchViewModel: SearchViewModel,
    private val settingsRepository: SettingsRepository  // NEW
) {
    private fun openUrlInBrowser(url: String) {
        val browserPackage = settingsRepository.getPreferredBrowser()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (browserPackage != null) {
            intent.`package` = browserPackage
        }
        context.startActivity(intent)
    }
}
```

### Customizable Settings (Planned)

- **Browser preference**: Chrome, Firefox, Brave, custom package
- **YouTube preference**: Official app, ReVanced, browser fallback
- **Search engine**: Google, DuckDuckGo, Bing, custom URL
- **File handling**: Default app or always show chooser

## Testing Strategy

Each handler can be unit tested independently:

```kotlin
// Example: Test ActionHandler
@Test
fun `handleLaunchApp starts activity and saves recent`() {
    val mockContext = mockk<Context>(relaxed = true)
    val mockViewModel = mockk<SearchViewModel>(relaxed = true)
    val handler = ActionHandler(mockContext, mockViewModel)
    
    val appInfo = AppInfo(packageName = "com.example", launchIntent = Intent())
    handler.handle(SearchAction.LaunchApp(appInfo))
    
    verify { mockContext.startActivity(any()) }
    verify { mockViewModel.saveRecentApp("com.example") }
}
```

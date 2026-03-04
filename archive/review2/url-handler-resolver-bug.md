# Bug: ResolverActivity ("Android System") shown as URL Handler

**Date:** 2026-02-25  
**Component:** `com.milki.launcher.domain.search.UrlHandlerResolver`  
**Severity:** Low (UI bug - shows incorrect icon/name)

## Problem Description

When the user has two or more apps that can handle a URL (e.g., YouTube and NewPipe) but hasn't set a "Default" app yet, Android returns a special system Activity called `ResolverActivity`. This is the popup that asks "Open with..." or "Just once" / "Always".

The current code in `UrlHandlerResolver.resolveUrlHandler()` uses `packageManager.resolveActivity()` with `MATCH_DEFAULT_ONLY`. When no default is set, this returns the ResolverActivity, and the code extracts its label ("Android System") and package name ("android"), causing the launcher to incorrectly show an Android system icon as the URL handler.

## Affected Code

File: `app/src/main/java/com/milki/launcher/domain/search/UrlHandlerResolver.kt`  
Method: `resolveUrlHandler()` (lines 69-115)

```kotlin
val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    packageManager.resolveActivity(
        intent,
        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
    )
} else {
    packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
}
// resolveInfo may be ResolverActivity here
```

## Root Cause

- `MATCH_DEFAULT_ONLY` only matches activities that have set themselves as the default handler
- If no default is set, Android falls back to the system ResolverActivity
- The code doesn't filter out this system activity before creating a `UrlHandlerApp`

## Potential Fixes

### Option 1: Filter out ResolverActivity (Recommended)

Add a check after resolving to skip the system resolver:

```kotlin
resolveInfo?.let { info ->
    // Check if this is the system resolver (the "Open with..." chooser)
    if (info.activityInfo.packageName == "android") {
        return null  // No default set
    }
    createHandlerApp(info)
}
```

For more robustness, also check the activity class name:
```kotlin
if (info.activityInfo.packageName == "android" ||
    info.activityInfo.name?.contains("ResolverActivity") == true) {
    return null
}
```

### Option 2: Return all handlers when resolver is detected

Instead of returning null, call `getAllUrlHandlers()` to get all available apps and let the UI handle showing a "Multiple apps" indicator.

### Option 3: Include ResolverActivity but mark it specially

Return a `UrlHandlerApp` with a flag indicating it's a chooser, so the UI can display something like "Open with..." or show the list of available apps.

## Recommendation

**Option 1** is the simplest and most correct fix. When there's no default handler, the behavior should be: no specific app is shown, and tapping the URL will show Android's native chooser.

After the fix, consider updating the UI to handle `null` gracefully - perhaps showing a "Choose app" badge or simply relying on the browser fallback.

## Related Issues

- Android's package visibility on API 30+ requires `<queries>` in manifest for this to work
- The `getAllUrlHandlers()` method already exists for showing all options

# Activity Label vs Application Label Inconsistency

## The Bug

There is an inconsistency in how the app fetches the display name for apps:

### In `getInstalledApps()` (line 233)
```kotlin
name = resolveInfo.loadLabel(pm).toString()
```
This gets the label from **ResolveInfo**, which is the name of the **specific Activity** (the launcher icon).

### In `getRecentApps()` (line 304)
```kotlin
val appInfo = pm.getApplicationInfoCompat(packageName)
name = pm.getApplicationLabel(appInfo).toString()
```
This gets the label from **ApplicationInfo**, which is the name of the **Application** as a whole.

## Why This Is a Problem

Many apps use different names for their Application and their Launcher Activity:

- **Google app**: Package name is "Google", but one launcher activity is named "Voice Search"
- **Gmail**: Application label is "Gmail", but the widget activity might be named "Gmail Widget"
- **Settings**: Many apps have multiple launcher activities for different features

If the user opens "Voice Search" activity:
- In the app drawer (getInstalledApps): Correctly shows "Voice Search"
- In recent apps (getRecentApps): Incorrectly shows "Google"

## The Fix

In `getRecentApps()`, we need to query for **ActivityInfo** instead of **ApplicationInfo**, and use `loadLabel()` on the ActivityInfo to match the behavior of `getInstalledApps()`.

### Step 1: Add a compat extension function for getting ActivityInfo

```kotlin
@Throws(PackageManager.NameNotFoundException::class)
fun PackageManager.getActivityInfoCompat(componentName: ComponentName): android.content.pm.ActivityInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getActivityInfo(componentName, PackageManager.ComponentInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        getActivityInfo(componentName, 0)
    }
}
```

### Step 2: Refactor getRecentApps() to use ActivityInfo

Replace the ApplicationInfo lookup with ActivityInfo lookup using the ComponentName:

```kotlin
// Inside getRecentApps() mapNotNull:
try {
    // Get ActivityInfo instead of ApplicationInfo
    val activityInfo = pm.getActivityInfoCompat(componentName)
    
    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = componentName
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
    
    AppInfo(
        // Use loadLabel on the activityInfo to match getInstalledApps()
        name = activityInfo.loadLabel(pm).toString(),
        packageName = packageName,
        activityName = activityName,
        launchIntent = launchIntent
    )
} catch (e: PackageManager.NameNotFoundException) {
    null
}
```

## Location of Bug

File: `app/src/main/java/com/milki/launcher/data/repository/AppRepositoryImpl.kt`

- Lines 289-312: The `getRecentApps()` function
- Line 292: Gets ApplicationInfo (wrong)
- Line 304: Uses getApplicationLabel (wrong)

Compare with:
- Lines 213-243: The `getInstalledApps()` function  
- Line 233: Uses resolveInfo.loadLabel() (correct)

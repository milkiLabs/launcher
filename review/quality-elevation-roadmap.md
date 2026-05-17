# Quality Elevation Roadmap

## Tier 1: Production Readiness (Must Have)

### 1. Crash Reporting

**Impact:** Cannot diagnose production crashes without it.
**Effort:** 1 hour

Add Firebase Crashlytics or Sentry. A launcher that crashes silently is a bricked phone.

```kotlin
// build.gradle.kts
implementation("com.google.firebase:firebase-crashlytics:19.3.0")
implementation("com.google.firebase:firebase-analytics:22.1.0")

// LauncherApplication.kt
FirebaseApp.initializeApp(this)
```

### 2. Edge-to-Edge Enforcement

**Impact:** App has black bars behind system bars on Android 10+. Looks unpolished.
**Effort:** 2 hours

```kotlin
// MainActivity.onCreate()
enableEdgeToEdge()
// Every screen needs Modifier.statusBarsPadding() / Modifier.navigationBarsPadding()
```

Search the codebase: `enableEdgeToEdge|WindowCompat|setDecorFitsSystemWindows` — **zero matches**. The app is not edge-to-edge.

### 3. Schema Migration Strategy

**Impact:** App updates that change `HomeItem` or settings schema will crash or silently lose user data.
**Effort:** 1-2 days

Both DataStores (`home_items`, `launcher_settings`) have **no version tracking**. Adding a new field to any `@Serializable` model breaks deserialization of old data.

**Required:**

- Add `schemaVersion` key to both DataStores
- Implement migration logic in `HomeSnapshotStore` and `SettingsRepositoryImpl`
- Add `@SerialName` to all serializable fields
- Test migration from v1 → v2 → v3

### 4. Memory Pressure Handling (onTrimMemory)

**Impact:** Launchers are always-resident. Without memory management, the system kills the process, causing full cold restart.
**Effort:** 1 day

`LauncherApplication` has **no `ComponentCallbacks2.onTrimMemory()` override**. The icon caches grow unbounded.

```kotlin
class LauncherApplication : Application(), ComponentCallbacks2 {
    override fun onTrimMemory(level: Int) {
        when (level) {
            TRIM_MEMORY_RUNNING_MODERATE -> AppIconMemoryCache.shrink(0.5f)
            TRIM_MEMORY_RUNNING_LOW -> AppIconMemoryCache.clear()
            TRIM_MEMORY_RUNNING_CRITICAL -> AppIconDiskSnapshotStore.clear()
        }
    }
}
```

### 5. Notification Badges

**Impact:** Users expect to see unread counts on app icons. Core launcher feature.
**Effort:** 2-3 days

Currently, `PinnedItem.kt:305-322` has a decorative badge (browser link icon), not a notification count.

**Required:**

- `ShortcutManagerCompat.getBadgeCount()` integration
- `LauncherApps.Callback` for notification changes
- Badge rendering on `AppIcon` composable
- Respect system notification dot settings

### 6. Integration Tests for Critical Flows

**Impact:** Zero meaningful instrumented tests. Critical launcher workflows have no automated coverage.
**Effort:** 1-2 weeks

The only instrumented test is the default template. Need Compose UI tests for:

- Pin app to home screen
- Drag-and-drop reorder
- Widget placement
- Folder creation and item management
- Search dialog open → filter → launch
- Backup and restore

---

## Tier 2: Reliability Engineering (Should Have)

These prevent data loss, improve stability, and make the app resilient.

### 7. Data Corruption Recovery

**Impact:** Users lose their home screen layout silently with no recovery path.
**Effort:** 1 day

When DataStore corruption occurs:

- `catchIoException()` emits `emptyPreferences()` — no logging, no user notification
- `HomeItemSerializer` defaults to a single docs shortcut — user loses entire layout
- No backup auto-restore after corruption

**Required:**

- Log corruption events (with Crashlytics)
- Attempt auto-restore from latest backup
- Show user notification: "Home layout was corrupted. Restored from backup."

### 8. Crash Recovery (Process Death)

**Impact:** After a crash, widget slots are empty, folder state is lost, widget IDs are orphaned.
**Effort:** 1 day

- `HomeViewModel` state is Flow-driven from DataStore (good — survives process death)
- But `WidgetHostManager` widget IDs are lost on process death
- `pendingWidgets` map is in-memory only
- No `savedInstanceState` for transient UI state

**Required:**

- Persist `pendingWidgets` to DataStore
- Restore widget host bindings on process recreation
- Save/restore transient UI state (open folder, search query)

### 9. Backup Completeness & Auto-Backup

**Impact:** Cloud backup/restore via Google's infrastructure is non-functional. Manual backup is incomplete.
**Effort:** 2 days

Current backup excludes: hidden apps, prefix configurations, trigger targets, recent apps.
`backup_rules.xml` and `data_extraction_rules.xml` are empty templates.

**Required:**

- Complete backup to include all user data
- Configure `backup_rules.xml` with proper include/exclude rules
- Add backup integrity verification (checksum)
- Test restore on fresh install

### 10. Battery Drain from Background Observers

**Impact:** Continuous background scanning on media-heavy devices; battery drain.
**Effort:** 1 day

- `PackageChangeMonitor` receiver has no `unregisterReceiver()` — leaks if Application is recreated
- `HomeAvailabilityPruner` fires on every MediaStore change (photo taken, download, etc.)
- Each change triggers a full home item scan
- No debouncing beyond `extraBufferCapacity = 1`

**Required:**

- Proper receiver lifecycle management
- Debounce prune requests (e.g., 5-second window)
- Skip scan if no pinned files exist

### 11. Baseline Profile Coverage Expansion

**Impact:** Unprofiled paths suffer JIT compilation jank on first user interaction.
**Effort:** 1 day

Current baseline profile only covers HOME ↔ DRAWER transitions. Missing:

- Search dialog open/filter/close
- Widget picker open/place
- Folder open/close
- Settings navigation
- Context menu open/dismiss

---

## Tier 3: UX Polish (Differentiators)

These are what make users say "this launcher feels great."

### 12. Predictive Back Animation (Android 14+)

**Impact:** Back gesture is instant-dismiss instead of animated. Feels jarring on modern Android.
**Effort:** 1-2 days

Uses legacy `OnBackPressedCallback`. No `PredictiveBackHandler` integration.

**Required:**

- Migrate to `androidx.activity:activity-ktx` predictive back API
- Add back progress callbacks for folder/search/drawer dismiss
- Animate folder collapse back to grid icon position

### 13. Folder Open/Close Animations

**Impact:** Folders pop in/out instantly. Jarring UX.
**Effort:** 1 day

**Required:**

- `animate*AsState` for folder expand/collapse
- Shared element transition from grid icon to folder popup
- Spring animation for folder open
- Icon grid animation inside folder

### 14. Widget Resize Handles

**Impact:** Users cannot resize widgets after placement.
**Effort:** 2-3 days

`clampResizeSpan()` exists in `WidgetHostManager` but is not exposed to UI. No visual resize handles.

**Required:**

- Visual drag grips on widget borders when selected
- Touch gesture to resize (drag edges/corners)
- Visual feedback for min/max span constraints
- Call `updateAppWidgetSize()` on resize commit

### 15. Wallpaper Integration

**Impact:** Visually flat compared to modern launchers (Pixel Launcher, Nova).
**Effort:** 2-3 days

**Required:**

- Parallax scrolling on home screen drag
- `RenderEffect.createBlurEffect()` for wallpaper dimming behind search/drawer
- `WallpaperColors` extraction for dynamic theming beyond Android 12

### 16. App Install Animation

**Impact:** New apps appear silently without user feedback.
**Effort:** 1 day

**Required:**

- Highlight newly installed apps in drawer (e.g., "NEW" badge for 24h)
- Animate new app appearance (fade + scale)
- `RecentlyChangedApps` row animation

### 17. Material 3 Expressive Transitions

**Impact:** Transitions are basic; no Material 3 expressive motion.
**Effort:** 2-3 days

**Required:**

- `SharedTransitionLayout` for folder open/close
- `AnimatedContent` for search results state changes
- Motion-based drag-drop animations
- Shared element transitions between drawer and home screen

---

## Tier 4: Engineering Excellence (Long-Term)

These improve developer velocity, release quality, and long-term maintainability.

### 18. Performance Monitoring

**Impact:** Cannot measure real-world performance degradation.
**Effort:** 1 day

`traceSection()` uses `android.os.Trace` (Systrace/Perfetto) — dev-only. No production telemetry.

**Required:**

- Firebase Performance Monitoring
- Runtime startup time logging
- Frame drop detection (`Choreographer.FrameCallback`)
- Slow frame reporting

### 19. Screenshot/Visual Regression Tests

**Impact:** UI regressions go undetected.
**Effort:** 1-2 days

**Required:**

- Add Roborazzi or Paparazzi
- Screenshot tests for `LauncherScreen`, `PinnedItem`, `AppSearchDialog`
- Visual regression detection for theme changes
- Run in CI on every PR

### 20. Feature Flags

**Impact:** Risky to ship incomplete features; no kill switch for problematic features.
**Effort:** 2-3 days

**Required:**

- Local feature flag system (DataStore-backed)
- Optional: Firebase Remote Config for remote control
- Flag-gate experimental features (widget resize, new search providers)
- Admin panel in settings to toggle flags (debug builds only)

### 21. Beta Distribution Channel

**Impact:** No way to distribute pre-release builds to testers.
**Effort:** 1 day

**Required:**

- Add `beta` build type
- Firebase App Distribution or Play Internal Testing
- Automated version code bumping
- Separate package name for beta (`com.milki.launcher.beta`)

### 22. Staged Rollouts & Play Store Publishing

**Impact:** All users get every release simultaneously; no controlled rollout.
**Effort:** 2-3 days

**Required:**

- Migrate from APK to AAB
- Google Play Publisher Gradle plugin
- Staged rollout percentages (10% → 50% → 100%)
- Automated rollback on crash spike

### 23. Accessibility Automated Tests

**Impact:** Accessibility regressions go undetected.
**Effort:** 1 day

**Required:**

- `AccessibilityChecks.enable()` in instrumented tests
- Compose semantics testing for screen readers
- Contrast ratio testing
- Touch target size validation

### 24. Performance Regression CI Gate

**Impact:** Performance regressions ship undetected.
**Effort:** 1-2 days

Benchmarks exist but are **not run in CI**. Need a performance baseline and regression detection.

**Required:**

- Store benchmark baselines in repo
- CI job that runs benchmarks on physical device (Firebase Test Lab)
- Fail PR if startup time or frame time regresses >5%

---

## Tier 5: Platform Modernization (Nice to Have)

### 25. Photo Picker Instead of MANAGE_EXTERNAL_STORAGE

**Impact:** App may be rejected from Play Store; over-privileged for file search.
**Effort:** 1 day

Replace `MANAGE_EXTERNAL_STORAGE` with `ActivityResultContracts.PickVisualMedia()`.

### 26. Per-App Language Support

**Impact:** Cannot override system language per-app on Android 13+.
**Effort:** 1 day

Add `AppCompatDelegate.setApplicationLocales()` and `android:localeConfig` resource.

### 27. Design System Completeness

**Impact:** Text hierarchy and component shapes rely on Material defaults.
**Effort:** 2-3 days

**Required:**

- Configure all 15 Material typography styles (only `bodyLarge` is set)
- Connect `CornerRadius` to Material 3 `Shapes` API
- Add semantic color tokens (success, warning, info)
- Theme toggle in settings (light/dark/dynamic)

---

## Prioritized Effort Estimate

| Tier                             | Items    | Total Effort   | Impact                                                   |
| -------------------------------- | -------- | -------------- | -------------------------------------------------------- |
| **Tier 1: Production Readiness** | 6 items  | 2-3 weeks      | Critical — app is not production-ready without these     |
| **Tier 2: Reliability**          | 5 items  | 1-2 weeks      | High — prevents data loss and battery drain              |
| **Tier 3: UX Polish**            | 6 items  | 2-3 weeks      | High — differentiates from competing launchers           |
| **Tier 4: Engineering**          | 7 items  | 2-3 weeks      | Medium — improves developer velocity and release quality |
| **Tier 5: Modernization**        | 3 items  | 1 week         | Low — nice to have for platform completeness             |
| **Total**                        | 27 items | **8-12 weeks** |                                                          |

---

## Quick Wins (Under 1 Day Each)

These can be done immediately with high ROI:

1. **Add Crashlytics** (1 hour) — visibility into production crashes
2. **Enable edge-to-edge** (2 hours) — looks polished on Android 10+
3. **Add `onTrimMemory()`** (2 hours) — prevents process kills
4. **Expand baseline profile** (4 hours) — eliminates first-use jank
5. **Configure backup rules** (4 hours) — enables cloud backup/restore
6. **Add Roborazzi** (4 hours) — catches UI regressions
7. **Add predictive back** (4 hours) — modern Android feel
8. **Replace MANAGE_EXTERNAL_STORAGE** (4 hours) — Play Store compliance

---

## What Makes a Launcher "Great"

A great launcher is judged by qualities that are invisible when done right and unbearable when done wrong:

| Quality                  | User Feels It When...                      | Current State                       |
| ------------------------ | ------------------------------------------ | ----------------------------------- |
| **Instant response**     | Tapping an app opens it in <200ms          | Good (baseline profiles)            |
| **Zero jank**            | Scrolling drawer is butter-smooth at 60fps | Partial (missing profile coverage)  |
| **Graceful degradation** | App survives crashes without losing layout | Poor (no crash recovery)            |
| **Memory efficient**     | Phone doesn't slow down after weeks of use | Poor (no onTrimMemory)              |
| **Battery friendly**     | Doesn't drain battery in background        | Poor (unbounded observers)          |
| **Visually polished**    | Animations feel intentional, not jarring   | Partial (missing folder animations) |
| **Accessible**           | Works for users with disabilities          | Poor (missing content descriptions) |
| **Reliable**             | Never loses data on update or crash        | Poor (no schema migration)          |
| **Discoverable**         | Users know what features exist             | Partial (no onboarding)             |
| **Customizable**         | Users can make it theirs                   | Partial (no theme toggle)           |

---

## Recommended Next Steps

1. **This week:** Add Crashlytics, enable edge-to-edge, add `onTrimMemory()`
2. **Next week:** Implement schema migration, expand baseline profile, configure backup rules
3. **Week 3:** Add notification badges, write integration tests for critical flows
4. **Week 4:** Implement predictive back, folder animations, widget resize handles
5. **Ongoing:** Fill remaining tiers based on user feedback and priorities

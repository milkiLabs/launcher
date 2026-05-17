# Build Configuration & Security Review

> Analysis of Gradle build setup, dependency hygiene, ProGuard/R8 rules, CI/CD workflows, manifest security, and build performance.

---

## 1. Security Issues

### 1.1 CRITICAL: Plaintext Keystore Passwords

**File:** `keystore.properties:2-4`
```properties
storePassword=eEHP5%&a8uXJJy
keyPassword=eEHP5%&a8uXJJy
```

The release keystore password is exposed. See `P0-Critical-Findings.md` for full details.

### 1.2 Backup Enabled with Empty Rules

**File:** `AndroidManifest.xml:52`
```xml
android:allowBackup="true"
```

**File:** `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` — both empty, backing up EVERYTHING.

**Risk:** All app data (DataStore preferences, serialized HomeItems, settings, contact cache) is backed up to Google Cloud with no exclusions.

**Fix:** Either set `android:allowBackup="false"` or explicitly `<exclude>` sensitive preferences.

### 1.3 Over-Privileged Permissions

| Permission | File:Line | Issue | Severity |
|------------|-----------|-------|----------|
| `MANAGE_EXTERNAL_STORAGE` | `AndroidManifest.xml:26-27` | Most sensitive storage permission; Play Store heavily restricts | HIGH |
| `CALL_PHONE` | `AndroidManifest.xml:7` | Allows direct calling without user confirmation; should use `ACTION_DIAL` | MEDIUM |
| `BIND_APPWIDGET` | `AndroidManifest.xml:10` | System-level permission; normal apps cannot hold it | LOW |
| `EXPAND_STATUS_BAR` | `AndroidManifest.xml:16-18` | System-level permission; silently ignored on non-system launchers | LOW |

### 1.4 Missing Security Configurations

| Missing | Impact | Severity |
|---------|--------|----------|
| `android:enableOnBackInvokedCallback="true"` | Required for proper back gesture on Android 14+ | MEDIUM |
| `android:networkSecurityConfig` | No protection against cleartext traffic | LOW |
| `BOOT_COMPLETED` receiver | Widgets not restored after reboot | MEDIUM |

---

## 2. Dependency Version Hygiene

### 2.1 Version Catalog Analysis

| Dependency | Version | Status | Notes |
|------------|---------|--------|-------|
| AGP | 9.0.1 | Cutting-edge | Requires Gradle 9.1+, may break third-party tooling |
| Kotlin | 2.3.20 | Very recent | Verify compatibility with all deps |
| Compose BOM | 2026.03.00 | Date-based scheme | Verify it exists on Maven |
| Koin BOM | 4.2.0 | Major version | Verify API compatibility with 3.x |
| Detekt | 2.0.0-alpha.2 | Alpha | Risky for production builds |
| Coil | 2.7.0 | Outdated | Commented out; remove from catalog or upgrade to 3.x |

### 2.2 Missing/Unused Dependencies

| Issue | File | Severity |
|-------|------|----------|
| Coil defined in catalog but commented out in build | `libs.versions.toml:14`, `app/build.gradle.kts:143` | LOW |
| `androidx.test:runner:1.7.0` hardcoded instead of version catalog | `baselineprofile/build.gradle.kts:55` | LOW |
| `kotlin-android` plugin not explicitly applied to app module | `app/build.gradle.kts:1-6` | MEDIUM |

### 2.3 Dependency Bloat

| Dependency | Issue | Severity |
|------------|-------|----------|
| `koin-android` + `koin-androidx-compose` | Potential overlap for Compose-only app | LOW |
| `compose.material-icons-extended` | Adds ~1.5MB to APK; audit actual usage | LOW |
| `lifecycle-runtime-ktx` + `lifecycle-runtime-compose` | Potential redundancy | LOW |

---

## 3. Build Configuration Issues

### 3.1 SDK Configuration

| Setting | Value | Issue | Severity |
|---------|-------|-------|----------|
| `compileSdk` | 36 | Testing against API 36 | ACCEPTABLE |
| `targetSdk` | 35 | Targeting API 35 while compiling against 36 | ACCEPTABLE |
| `minSdk` | 24 | Android 7.0; covers ~95%+ of devices | GOOD |
| `ndkVersion` | 27.1.12297006 | No native code in app; unnecessary | LOW |

### 3.2 Java/Kotlin Configuration

| Issue | File | Severity |
|-------|------|----------|
| Java 11 compatibility (conservative) | `app/build.gradle.kts:98-101` | LOW |
| Missing `kotlinOptions { jvmTarget = "11" }` | `app/build.gradle.kts` | MEDIUM |

### 3.3 Version Code/Name Static

**File:** `app/build.gradle.kts:33-34`
```kotlin
versionCode = 1
versionName = "1.0"
```

Hardcoded. For CI/CD release workflows, these should be dynamic (git tags, env vars, or `version.properties`).

---

## 4. ProGuard/R8 Rules — SEVERELY INCOMPLETE

**File:** `app/proguard-rules.pro` — Contains ONLY default comments. Zero actual keep rules.

### 4.1 Required Rules

```proguard
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class com.milki.launcher.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.milki.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Koin
-keep class org.koin.** { *; }
-keepclassmembers class com.milki.launcher.core.di.** { *; }

# Device Admin
-keep class com.milki.launcher.core.deviceadmin.LauncherDeviceAdminReceiver { *; }

# DataStore
-keep class androidx.datastore.** { *; }
```

### 4.2 Impact

**Release builds will crash** — R8 will strip serialization serializers, Koin modules, and reflected classes. The app has `isMinifyEnabled = true` in release build type.

---

## 5. CI/CD Workflow Issues

### 5.1 Duplicate Release Workflows

| File | Issue | Severity |
|------|-------|----------|
| `.github/workflows/android-release.yml` | Duplicate of `release-android.yml` | MEDIUM |
| `.github/workflows/release-android.yml` | Duplicate of `android-release.yml` | MEDIUM |

### 5.2 Missing CI Checks

**File:** `.github/workflows/ci-android.yml`

Missing:
- `./gradlew :app:lintDebug` — Android lint checks
- `./gradlew :app:detekt` — Detekt static analysis
- `./gradlew :app:check` — All checks combined

### 5.3 JDK Version Mismatch

| Workflow | JDK Version | Issue |
|----------|-------------|-------|
| `ci-android.yml` | JDK 17 | Should match release workflow |
| `release-android.yml` | JDK 21 | AGP 9.x recommends JDK 21 |

### 5.4 Missing Security Practices

| Issue | Severity |
|-------|----------|
| No dependency vulnerability scanning (Dependabot, OWASP) | MEDIUM |
| Actions not pinned to commit SHAs | LOW |
| No APK integrity/verification step | LOW |
| Tests run against Debug, not Release | LOW |

---

## 6. Detekt Configuration

### 6.1 Current Configuration

**File:** `config/detekt/detekt.yml`

| Setting | Value | Assessment |
|---------|-------|------------|
| `warningsAsErrors` | `false` | Should be `true` in CI |
| `allowedFunctionParameters` | 8 | Very high (default: 5) |
| `allowedConstructorParameters` | 9 | Very high (default: 6) |
| `allowedFunctionsPerFile` | 20 | Extremely permissive (default: 11) |
| `allowedFunctionsPerClass` | 20 | Extremely permissive (default: 11) |

### 6.2 Missing Rule Sets

| Rule Set | Status |
|----------|--------|
| `complexity` | Enabled |
| `naming` | Enabled |
| `style` | Enabled |
| `empty-blocks` | MISSING |
| `exceptions` | MISSING |
| `performance` | MISSING |
| `potential-bugs` | MISSING |
| `formatting` | MISSING |
| `coroutines` | MISSING |

### 6.3 Baseline File

**File:** `config/detekt/app-baseline.xml` — 122 lines of suppressed issues

The baseline suppresses issues across all categories. New code should not add to it. The baseline should be periodically reduced.

---

## 7. .gitignore Completeness

### 7.1 Issues

| Issue | Severity |
|-------|----------|
| `keystore.properties` in .gitignore but file exists (likely tracked) | HIGH |
| Redundant entries: `/local.properties` and `local.properties` | LOW |
| Missing: `*.log`, `*.hprof`, `*.jks`, `*.keystore`, `.env`, `*.apk`, `*.aab` | LOW |

### 7.2 Recommendation

```gitignore
# Add:
*.log
*.hprof
*.jks
*.keystore
secrets/
.env
.env.*
*.apk
*.aab
*.ap_
*.dex
```

---

## 8. Build Performance

### 8.1 Good Practices

| Setting | File | Status |
|---------|------|--------|
| Configuration cache | `gradle.properties:1` | ENABLED |
| Parallel execution | `gradle.properties:2` | ENABLED |
| Build cache | `gradle.properties:3` | ENABLED |
| Non-transitive R class | `gradle.properties:7` | ENABLED |
| Gradle wrapper SHA verification | `gradle-wrapper.properties:4` | ENABLED |

### 8.2 Improvements

| Issue | File | Severity |
|-------|------|----------|
| JVM heap 2GB is low for AGP 9.x + Kotlin 2.x | `gradle.properties:4` | MEDIUM |

**Recommendation:**
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -XX:+UseParallelGC
```

---

## 9. Manifest Security

### 9.1 Exported Components

| Component | Exported | Assessment |
|-----------|----------|------------|
| `MainActivity` | `true` | CORRECT — launcher home |
| `SettingsActivity` | `false` | CORRECT |
| `LauncherDeviceAdminReceiver` | `true` with `BIND_DEVICE_ADMIN` | CORRECT |

### 9.2 Permission Analysis

| Permission | Type | Needed? |
|------------|------|---------|
| `CALL_PHONE` | Dangerous | Questionable — use `ACTION_DIAL` |
| `READ_CONTACTS` | Dangerous | Yes — contact search |
| `BIND_APPWIDGET` | Signature/privileged | NO — remove |
| `EXPAND_STATUS_BAR` | Protected | NO — remove |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Dangerous | Yes — legacy support |
| `READ_MEDIA_IMAGES/VIDEO/AUDIO` | Dangerous | Yes — file search |
| `MANAGE_EXTERNAL_STORAGE` | Special | Questionable |
| `REQUEST_DELETE_PACKAGES` | Normal | Yes — uninstall apps |
| `QUERY_ALL_PACKAGES` | Special | Yes — launcher |
| `REQUEST_PIN_SHORTCUTS` | Normal | Yes — shortcut pinning |

---

## 10. Priority Summary

| Priority | Finding | File | Impact |
|----------|---------|------|--------|
| P0 | Rotate keystore — passwords exposed | `keystore.properties` | Security |
| P0 | Add ProGuard rules for serialization + Koin | `app/proguard-rules.pro` | Release crash |
| P1 | Restrict backup/extraction rules | `backup_rules.xml` | Privacy |
| P1 | Remove duplicate release workflow | `.github/workflows/` | CI confusion |
| P1 | Add lint/detekt to CI | `.github/workflows/ci-android.yml` | Quality gate |
| P1 | Fix JDK version mismatch (17 vs 21) | `.github/workflows/ci-android.yml` | Build consistency |
| P2 | Remove impossible system permissions | `AndroidManifest.xml` | Manifest hygiene |
| P2 | Enable `warningsAsErrors` for Detekt in CI | `config/detekt/detekt.yml` | Quality gate |
| P2 | Increase Gradle JVM heap | `gradle.properties:4` | Build performance |
| P3 | Add `enableOnBackInvokedCallback` | `AndroidManifest.xml` | Android 14+ support |
| P3 | Remove unused Coil from catalog | `libs.versions.toml:14` | Confusion |
| P3 | Add `kotlin-android` plugin to app module | `app/build.gradle.kts:1-6` | Build stability |

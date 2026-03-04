# Security Audit Report - Milki Launcher

**Audit Date:** 2026-02-25  
**Auditor:** Security Review  
**Project:** Milki Launcher (Android Kotlin)  
**Package:** com.milki.launcher  

---

## Executive Summary

This security audit identified **6 security issues** across the codebase:
- **1 Critical** - Hardcoded secrets in version control
- **1 High** - Insecure file permissions
- **2 Medium** - Logging and backup configuration
- **2 Low** - Permission and export considerations

The most critical issue is the presence of hardcoded keystore passwords in the `keystore.properties` file that appears to be in version control.

---

## Critical Issues

### 1. Hardcoded Keystore Secrets in Version Control

| Property | Value |
|----------|-------|
| **Severity** | Critical |
| **File** | `keystore.properties` |
| **Lines** | 1-4 |
| **Category** | Hardcoded Secrets |

**Description:**  
The `keystore.properties` file contains hardcoded keystore passwords stored in plaintext:

```properties
storeFile=/home/alien/secrets/launcher-release-keystore.jks
storePassword=lkjlsakjsfd
keyAlias=release
keyPassword=lkjlsakjsfd
```

**Attack Vector:**  
1. An attacker with read access to the repository can extract the keystore passwords
2. The keystore file path is also exposed, potentially allowing access to the actual signing key
3. With access to the signing key, an attacker can:
   - Sign malicious apps as the original developer
   - Create fake updates for the application
   - Bypass Android's signature verification

**Impact:**  
- Complete compromise of app signing integrity
- Potential for supply chain attacks
- Reputation damage if malicious apps are distributed under the developer's identity

**Suggested Fix:**
1. Immediately rotate all keystore passwords
2. Remove `keystore.properties` from version control:
   ```bash
   git rm --cached keystore.properties
   ```
3. Add to `.gitignore`:
   ```
   keystore.properties
   local.properties
   ```
4. Use environment variables or CI/CD secrets for signing:
   ```kotlin
   // build.gradle.kts
   storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
   keyPassword = System.getenv("KEY_PASSWORD") ?: ""
   ```
5. For local development, create a template file `keystore.properties.example` with placeholder values

---

## High Severity Issues

### 2. Insecure File Permissions on keystore.properties

| Property | Value |
|----------|-------|
| **Severity** | High |
| **File** | `keystore.properties` |
| **Current Permissions** | 644 (world-readable) |
| **Category** | File Permissions |

**Description:**  
The `keystore.properties` file has permissions `644` (rw-r--r--), making it readable by any user on the system.

**Attack Vector:**  
Any user with filesystem access can read the keystore passwords:
```bash
cat /media/Maind/zahrawi/productivity/launcher/keystore.properties
```

**Impact:**  
- Local privilege escalation
- Password exposure to other users on shared systems

**Suggested Fix:**
```bash
chmod 600 keystore.properties
```
This restricts read/write access to the file owner only.

---

## Medium Severity Issues

### 3. Logging of Potentially Sensitive Data

| Property | Value |
|----------|-------|
| **Severity** | Medium |
| **File** | `FilesRepositoryImpl.kt` |
| **Lines** | 68, 77, 100, 122, 160, 164, 184, 189, 259, 266 |
| **Category** | Information Disclosure |

**Description:**  
The `FilesRepositoryImpl` class logs file names, search queries, and file metadata using `Log.d()` and `Log.e()`:

```kotlin
// Line 77
Log.d(TAG, "Searching files with query: $query")

// Line 122
Log.d(TAG, "Querying URI: $uri with query: $query")

// Line 160
Log.d(TAG, "Found file: $name, mimeType: $mimeType, size: $size")
```

**Attack Vector:**  
1. On rooted devices, any app can read logcat output
2. Debug logs persist in system logs and can be extracted via ADB
3. File names may contain sensitive information (e.g., "tax_return_2024.pdf", "medical_records.pdf")

**Impact:**  
- Information disclosure about user's files
- Privacy violation
- Potential exposure of file existence and names

**Suggested Fix:**
1. Remove debug logging in production builds:
   ```kotlin
   if (BuildConfig.DEBUG) {
       Log.d(TAG, "Searching files with query: $query")
   }
   ```
2. Use ProGuard/R8 rules to strip logging:
   ```proguard
   -assumenosideeffects class android.util.Log {
       public static boolean isLoggable(java.lang.String, int);
       public static int v(...);
       public static int d(...);
       public static int i(...);
   }
   ```
3. Consider using a logging library that supports different log levels per build type

---

### 4. Backup Rules Not Configured

| Property | Value |
|----------|-------|
| **Severity** | Medium |
| **File** | `app/src/main/res/xml/backup_rules.xml` |
| **Lines** | 1-13 |
| **Category** | Data Exposure |

**Description:**  
The backup rules file is empty/commented out, meaning all app data including DataStore files will be backed up to Google Drive:

```xml
<full-backup-content>
    <!-- All rules commented out -->
</full-backup-content>
```

**Data at Risk:**
- `launcher_prefs.preferences_pb` - Recent apps list (reveals app usage patterns)
- `recent_contacts.preferences_pb` - Recent contacts (reveals call patterns)
- `launcher_settings.preferences_pb` - User settings
- `home_items.preferences_pb` - Pinned items (reveals user preferences)

**Attack Vector:**  
1. An attacker with Google account access can restore app data to another device
2. Backup data may be accessible through Google Takeout
3. Physical access to device allows data extraction via backup

**Impact:**  
- User privacy exposure
- Information disclosure about app usage and contacts

**Suggested Fix:**
Configure backup rules to exclude sensitive data:
```xml
<full-backup-content>
    <!-- Exclude DataStore files containing sensitive data -->
    <exclude domain="file" path="datastore/recent_contacts.preferences_pb"/>
    <exclude domain="file" path="datastore/launcher_prefs.preferences_pb"/>
    <exclude domain="file" path="datastore/home_items.preferences_pb"/>
    
    <!-- Or exclude all datastore files -->
    <exclude domain="file" path="datastore/"/>
</full-backup-content>
```

Also update `data_extraction_rules.xml`:
```xml
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="datastore/"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="datastore/"/>
    </device-transfer>
</data-extraction-rules>
```

---

## Low Severity Issues

### 5. MANAGE_EXTERNAL_STORAGE Permission

| Property | Value |
|----------|-------|
| **Severity** | Low |
| **File** | `AndroidManifest.xml` |
| **Lines** | 41-42 |
| **Category** | Permissions |

**Description:**  
The app requests the `MANAGE_EXTERNAL_STORAGE` permission:

```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />
```

**Context:**  
This is a special permission that grants access to all files on the device. While this is necessary for the launcher's file search feature, it has implications:

**Considerations:**
1. **Play Store Review:** Apps using this permission undergo additional review
2. **User Trust:** Users may be concerned about granting full file access
3. **Data Access:** The app can potentially read any non-encrypted file on the device

**Mitigations Already in Place:**
- The permission is only requested when the user uses the file search feature
- The app properly checks for permission before accessing files
- Images and videos are excluded from search results (FilesRepositoryImpl.kt:163-166)

**Recommendation:**  
Document clearly in the app why this permission is needed and what data is accessed. Consider adding a privacy policy explaining file access scope.

---

### 6. Exported Activities

| Property | Value |
|----------|-------|
| **Severity** | Low |
| **File** | `AndroidManifest.xml` |
| **Lines** | 159-201, 219-237 |
| **Category** | Exported Components |

**Description:**  
Both `MainActivity` and `SettingsActivity` are exported with `android:exported="true"`.

**MainActivity (Lines 159-201):**
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask"
    ...>
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        ...
    </intent-filter>
</activity>
```

**Analysis:**  
This is **intentional and required** for a launcher application:
- The HOME category requires the activity to be exported
- The system needs to launch it when the home button is pressed

**Potential Attack Vectors:**
1. Third-party apps can launch the launcher or settings activity
2. Intent data could be passed to these activities

**Mitigations in Place:**
- The activities don't process incoming intent data unsafely
- `onNewIntent()` in MainActivity.kt:228-243 properly validates the intent action

**Recommendation:**  
Ensure any future intent handling continues to validate incoming data. Document that these exports are intentional.

---

## Security Best Practices Observed

The codebase follows several security best practices:

### Positive Findings

1. **Permission Handling (Good)**
   - Runtime permissions are properly requested before accessing sensitive data
   - Permission states are checked before operations (ContactsRepositoryImpl.kt:142-144, FilesRepositoryImpl.kt:67-69)
   - Proper permission explanation in AndroidManifest.xml comments

2. **SQL Injection Prevention (Good)**
   - Uses parameterized queries with selectionArgs for ContentProvider queries:
     ```kotlin
     // ContactsRepositoryImpl.kt:175-180
     val selectionArgs = arrayOf(
         "%$queryLower%",
         ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
         ...
     )
     ```
   - Query parameters are properly escaped via Android's ContentResolver API

3. **Intent Safety (Good)**
   - Uses explicit intents for launching apps (AppRepositoryImpl.kt:220-228)
   - Validates intent resolution before launching (ActionExecutor.kt:147-156, 197)
   - Uses chooser for file opening to prevent implicit intent hijacking

4. **No WebView Usage (Good)**
   - No WebView components found in the codebase
   - URLs are opened in external browser/app, reducing attack surface

5. **No Hardcoded API Keys (Good)**
   - No API keys or secrets found in source code
   - Web search URLs use public endpoints without authentication

6. **DataStore for Sensitive Data (Good)**
   - Uses Jetpack DataStore instead of SharedPreferences
   - Data is stored in app-private storage (`/data/data/<package>/files/datastore/`)

---

## Summary Table

| # | Issue | Severity | File | Status |
|---|-------|----------|------|--------|
| 1 | Hardcoded keystore secrets | Critical | keystore.properties | Requires Immediate Action |
| 2 | Insecure file permissions | High | keystore.properties | Requires Action |
| 3 | Logging of sensitive data | Medium | FilesRepositoryImpl.kt | Should Fix |
| 4 | Backup rules not configured | Medium | backup_rules.xml | Should Fix |
| 5 | MANAGE_EXTERNAL_STORAGE permission | Low | AndroidManifest.xml | Document & Monitor |
| 6 | Exported activities | Low | AndroidManifest.xml | Intentional, Monitor |

---

## Recommendations Priority

### Immediate (Within 24 hours)
1. Remove `keystore.properties` from version control
2. Rotate all keystore passwords
3. Set proper file permissions on sensitive files

### Short-term (Within 1 week)
1. Configure backup rules to exclude sensitive DataStore files
2. Remove or protect debug logging in production builds
3. Add `keystore.properties` to `.gitignore`

### Long-term (Within 1 month)
1. Implement CI/CD secrets management for signing
2. Add privacy policy documentation
3. Consider implementing certificate pinning if network features are added
4. Enable ProGuard/R8 with logging removal rules

---

## Files Reviewed

| File | Lines | Security Relevance |
|------|-------|-------------------|
| AndroidManifest.xml | 240 | Permissions, exported components |
| keystore.properties | 4 | **Critical** - Hardcoded secrets |
| build.gradle.kts | 357 | Signing configuration |
| MainActivity.kt | 244 | Intent handling |
| SettingsActivity.kt | 70 | Activity configuration |
| LauncherApplication.kt | 153 | App initialization |
| PermissionHandler.kt | 331 | Permission management |
| ActionExecutor.kt | 313 | Intent execution |
| SearchViewModel.kt | 429 | State management |
| ContactsRepositoryImpl.kt | 651 | ContentProvider queries |
| FilesRepositoryImpl.kt | 291 | **Medium** - Logging |
| AppRepositoryImpl.kt | 359 | PackageManager queries |
| HomeRepositoryImpl.kt | 365 | Data persistence |
| SettingsRepositoryImpl.kt | 199 | Settings storage |
| UrlHandlerResolver.kt | 313 | URL handling |
| backup_rules.xml | 13 | **Medium** - Backup config |
| data_extraction_rules.xml | 19 | Data backup config |

---

## Conclusion

The most critical security issue is the presence of hardcoded keystore secrets in version control. This should be addressed immediately by removing the file from the repository, rotating all passwords, and implementing proper secrets management.

The codebase generally follows good security practices for an Android launcher application. The permission model is well-implemented, and the app properly handles runtime permissions. No SQL injection vulnerabilities were found as the app uses parameterized queries.

However, the logging and backup configuration issues could lead to information disclosure and should be addressed before a production release.

---

*This report was generated as part of a security audit. All findings should be validated and addressed according to your organization's security policies.*

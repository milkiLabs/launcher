# Build Configuration & Security Review

> Analysis of Gradle build setup, dependency hygiene, ProGuard/R8 rules, CI/CD workflows, manifest security, and build performance.

---

### 1.4 Missing Security Configurations

| Missing                                      | Impact                                          | Severity |
| -------------------------------------------- | ----------------------------------------------- | -------- |
| `android:enableOnBackInvokedCallback="true"` | Required for proper back gesture on Android 14+ | MEDIUM   |

---

## 2. Dependency Version Hygiene

### 3.1 SDK Configuration

| Setting      | Value | Issue                                       | Severity   |
| ------------ | ----- | ------------------------------------------- | ---------- |
| `compileSdk` | 36    | Testing against API 36                      | ACCEPTABLE |
| `targetSdk`  | 35    | Targeting API 35 while compiling against 36 | ACCEPTABLE |

### 3.2 Java/Kotlin Configuration

| Issue                                        | File                          | Severity |
| -------------------------------------------- | ----------------------------- | -------- |
| Java 11 compatibility (conservative)         | `app/build.gradle.kts:98-101` | LOW      |
| Missing `kotlinOptions { jvmTarget = "11" }` | `app/build.gradle.kts`        | MEDIUM   |

### 3.3 Version Code/Name Static

**File:** `app/build.gradle.kts:33-34`

```kotlin
versionCode = 1
versionName = "1.0"
```

Hardcoded. For CI/CD release workflows, these should be dynamic (git tags, env vars, or `version.properties`).

---

## 5. CI/CD Workflow Issues

### 5.2 Missing CI Checks

**File:** `.github/workflows/ci-android.yml`

Missing:

- `./gradlew :app:lintDebug` — Android lint checks
- `./gradlew :app:detekt` — Detekt static analysis
- `./gradlew :app:check` — All checks combined

### 5.4 Missing Security Practices

| Issue                                                    | Severity |
| -------------------------------------------------------- | -------- |
| No dependency vulnerability scanning (Dependabot, OWASP) | MEDIUM   |
| Actions not pinned to commit SHAs                        | LOW      |
| No APK integrity/verification step                       | LOW      |
| Tests run against Debug, not Release                     | LOW      |

---

## 6. Detekt Configuration

### 6.1 Current Configuration

**File:** `config/detekt/detekt.yml`

| Setting                        | Value   | Assessment                         |
| ------------------------------ | ------- | ---------------------------------- |
| `warningsAsErrors`             | `false` | Should be `true` in CI             |
| `allowedFunctionParameters`    | 8       | Very high (default: 5)             |
| `allowedConstructorParameters` | 9       | Very high (default: 6)             |
| `allowedFunctionsPerFile`      | 20      | Extremely permissive (default: 11) |
| `allowedFunctionsPerClass`     | 20      | Extremely permissive (default: 11) |

### 6.2 Missing Rule Sets

| Rule Set         | Status  |
| ---------------- | ------- |
| `complexity`     | Enabled |
| `naming`         | Enabled |
| `style`          | Enabled |
| `empty-blocks`   | MISSING |
| `exceptions`     | MISSING |
| `performance`    | MISSING |
| `potential-bugs` | MISSING |
| `formatting`     | MISSING |
| `coroutines`     | MISSING |

### 6.3 Baseline File

**File:** `config/detekt/app-baseline.xml` — 122 lines of suppressed issues

The baseline suppresses issues across all categories. New code should not add to it. The baseline should be periodically reduced.

---

## 8. Build Performance

### 8.2 Improvements

| Issue                                        | File                  | Severity |
| -------------------------------------------- | --------------------- | -------- |
| JVM heap 2GB is low for AGP 9.x + Kotlin 2.x | `gradle.properties:4` | MEDIUM   |

**Recommendation:**

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -XX:+UseParallelGC
```

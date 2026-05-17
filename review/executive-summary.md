# Code Review — Executive Summary

> Milki Launcher — Comprehensive codebase audit conducted on 2026-05-17

---

## Project Overview

**Milki Launcher** is a custom Android launcher built with Kotlin and Jetpack Compose. It features a multi-mode search system, grid-based home screen with drag-and-drop, widget hosting, folder support, and configurable search providers.

**Stats:**
- 187 Kotlin source files
- 27 unit test files
- 1 instrumented test file (template only)
- 6 Koin DI modules
- Single Gradle module (`:app`)

---

## Overall Assessment

| Category | Rating | Summary |
|----------|--------|---------|
| Architecture | **B+** | Clean layer separation but with notable violations |
| Code Quality | **B-** | Well-structured but with dead code and duplication |
| Performance | **B** | Good caching and async patterns, some hot-path inefficiencies |
| Compose UI | **B-** | Solid component design but missing stability annotations and theming issues |
| State Management | **B-** | Good MVI patterns but with concurrency bugs |
| Testing | **D+** | 27 unit tests but 40+ critical files untested |
| Security | **C-**** | Keystore passwords exposed, missing ProGuard rules |
| Accessibility | **D** | Missing content descriptions, small touch targets, contrast issues |
| Build/CI | **C+** | Good Gradle setup but incomplete CI checks |
| Developer Experience | **B-** | Good docs but high cognitive load in complex areas |

---

## Critical Findings (Must Fix Before Release)

### 1. Security: Keystore Passwords Exposed
- **File:** `keystore.properties`
- **Impact:** Anyone with repo access can sign malicious APKs
- **Action:** Rotate keystore, purge from history, use secrets only

### 2. Build: ProGuard Rules Missing
- **File:** `app/proguard-rules.pro`
- **Impact:** Release builds will crash (R8 strips serialization/Koin)
- **Action:** Add keep rules for kotlinx.serialization, Koin, Compose, Device Admin

### 3. Memory Leak: Uncancelled CoroutineScope
- **File:** `HomeRepositoryImpl.kt:28`
- **Impact:** DataStore Flow collects forever, memory leak
- **Action:** Cancel scope on repository close or use lifecycle-aware scope

### 4. Concurrency: Thread-Unsafe LinkedHashMap
- **File:** `HomeViewModel.kt:73`
- **Impact:** `ConcurrentModificationException` in widget placement
- **Action:** Guard with Mutex or use `MutableStateFlow<Map>`

### 5. Concurrency: CancellationException Swallowed
- **File:** `HomeViewModel.kt:158`
- **Impact:** Structured concurrency broken, coroutines cannot be cancelled
- **Action:** Use try/catch and re-throw `CancellationException`

### 6. Resource Leak: Widget ID Leak
- **File:** `HomeViewModel.kt:556-577`
- **Impact:** Widget IDs exhausted over time
- **Action:** Ensure deallocation on coroutine cancellation

### 7. Architecture: Domain Layer Leaks Android Types
- **Files:** `HomeItem.kt`, `LauncherBackupRepository.kt`
- **Impact:** Domain layer not testable without Android framework
- **Action:** Move framework type mapping to data/presentation layer

### 8. Architecture: coreModule Violates Own Rules
- **File:** `CoreModule.kt:132-141`
- **Impact:** Dependency graph inverted, modularization blocked
- **Action:** Create dedicated `backupModule`

---

## High-Priority Findings

| # | Category | Finding | File |
|---|----------|---------|------|
| 9 | Design | `SettingsRepository` is a god interface (27 methods) | `SettingsRepository.kt` |
| 10 | Memory | `HomeViewModel` holds `Context` reference | `HomeViewModel.kt:46` |
| 11 | Architecture | Presentation imports data-layer implementation | `SearchViewModelSettingsAdapter.kt` |
| 12 | UX | Hardcoded white text fails on light wallpapers | `PinnedItem.kt:184` |
| 13 | UX | Hardcoded menu colors break in light mode | `ItemActionMenu.kt:142` |
| 14 | A11y | Missing content descriptions on interactive elements | Multiple |
| 15 | A11y | Touch targets below 48dp in multiple places | `Spacing.kt` |
| 16 | Testing | 40+ critical files with zero test coverage | Multiple |
| 17 | Build | Duplicate release workflows | `.github/workflows/` |
| 18 | CI | Missing lint/detekt checks in CI | `ci-android.yml` |
| 19 | Platform | No widget restore on reboot | N/A |
| 20 | UX | No empty state for home screen | `LauncherScreen.kt` |

---

## Medium-Priority Findings

| # | Category | Finding | File |
|---|----------|---------|------|
| 21 | Performance | `ActionShortcutIcon` resolves package during composition | `PinnedItem.kt:288` |
| 22 | Compose | Domain models lack `@Immutable` annotations | `domain/model/*.kt` |
| 23 | Code Quality | 6 copies of CSV splitting logic | Multiple |
| 24 | Code Quality | 3 copies of DataStore IOException recovery | Multiple |
| 25 | Code Quality | `HomeModelWriter` 724 lines, violates SRP | `HomeModelWriter.kt` |
| 26 | UX | Toast errors inaccessible to TalkBack | `PinnedItemOpener.kt` |
| 27 | UX | No loading state for home screen | `LauncherScreen.kt` |
| 28 | UX | No network state handling for web search | Search providers |
| 29 | Lifecycle | `permissionHandler` not checked for initialization | `LauncherHostRuntime.kt:152` |
| 30 | Build | JDK version mismatch in CI (17 vs 21) | Workflows |
| 31 | Platform | Forced portrait orientation | `AndroidManifest.xml` |
| 32 | A11y | RTL positioning not used in menu provider | `ItemActionMenu.kt` |
| 33 | Testing | No instrumentation tests | `androidTest/` |
| 34 | DX | No ktlint formatting enforcement | N/A |
| 35 | DX | No Jacoco coverage reporting | N/A |

---

## Strengths

1. **Clean architecture foundation** — Clear layer separation with domain/data/presentation
2. **Well-designed DI** — Feature-oriented Koin modules with documented dependency rules
3. **Good async patterns** — Generation counters, `collectLatest`, proper scope management (mostly)
4. **Rich domain models** — Sealed classes, factory methods, serialization support
5. **Comprehensive documentation** — Architecture, conventions, contributing, performance docs
6. **Good Gradle setup** — Configuration cache, parallel execution, version catalog
7. **Baseline profiles** — Performance optimization for startup
8. **Deterministic reorder engine** — Tested drag-drop behavior
9. **Good empty states** — Most surfaces have appropriate empty/loading states
10. **Haptic feedback** — Consistent use of haptics for drag-drop interactions

---

## Remediation Roadmap

### Phase 1: Critical Fixes (Week 1)
- [ ] Rotate keystore and purge from git history
- [ ] Add ProGuard/R8 rules
- [ ] Fix `HomeRepositoryImpl` memory leak
- [ ] Fix `pendingWidgets` thread safety
- [ ] Fix `runCatching` CancellationException handling
- [ ] Fix widget ID leak on cancellation

### Phase 2: Architecture Cleanup (Week 2-3)
- [ ] Remove Android types from domain layer
- [ ] Fix `coreModule` dependency inversion
- [ ] Split `SettingsRepository` interface
- [ ] Remove `Context` from `HomeViewModel`
- [ ] Fix presentation/data layer coupling

### Phase 3: UX & Accessibility (Week 3-4)
- [ ] Fix hardcoded colors for theming
- [ ] Add content descriptions to interactive elements
- [ ] Fix touch target sizes
- [ ] Add home screen empty state
- [ ] Replace Toast errors with Snackbar/inline errors
- [ ] Add loading state for home screen

### Phase 4: Testing & CI (Week 4-5)
- [ ] Add coroutines test library and MockK
- [ ] Write tests for 10 most critical untested files
- [ ] Add lint/detekt to CI
- [ ] Add Jacoco coverage reporting
- [ ] Add ktlint formatting
- [ ] Consolidate duplicate release workflows

### Phase 5: Polish & DX (Week 5-6)
- [ ] Add `@Immutable` annotations to domain models
- [ ] Extract duplicated logic (CSV splitting, IOException recovery)
- [ ] Add `@Preview` annotations to key composables
- [ ] Create ADRs for key decisions
- [ ] Add widget restore on reboot
- [ ] Add network state handling

---

## File-by-File Finding Counts

| File | Findings | Severity |
|------|----------|----------|
| `HomeViewModel.kt` | 8 | HIGH |
| `HomeRepositoryImpl.kt` | 4 | CRITICAL |
| `CoreModule.kt` | 3 | HIGH |
| `SettingsRepository.kt` | 3 | HIGH |
| `PinnedItem.kt` | 5 | HIGH |
| `ItemActionMenu.kt` | 4 | HIGH |
| `LauncherHostRuntime.kt` | 3 | MEDIUM |
| `HomeModelWriter.kt` | 3 | MEDIUM |
| `SearchViewModelPipelineCoordinator.kt` | 2 | MEDIUM |
| `WidgetHostManager.kt` | 3 | MEDIUM |

---

## Conclusion

Milki Launcher has a **solid architectural foundation** with clean layer separation, well-designed dependency injection, and rich domain models. The codebase demonstrates thoughtful engineering in areas like the deterministic reorder engine, generation-counter-based search cancellation, and comprehensive caching strategies.

However, there are **critical issues that must be addressed before any production release**: exposed keystore passwords, missing ProGuard rules, memory leaks, and concurrency bugs. Additionally, the app has significant **accessibility gaps** and **test coverage deficiencies** that affect both user experience and developer confidence.

The recommended remediation roadmap prioritizes security and correctness first, followed by architecture cleanup, UX improvements, and finally testing/CI hardening. Following this roadmap will transform the codebase from a functional prototype into a production-ready launcher.

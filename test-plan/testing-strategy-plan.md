# Launcher App Comprehensive Testing Plan

## 1. Introduction and Goals

This document outlines a complete testing strategy for the Android Launcher project. Moving from minimal standard tests to a robust, layered testing suite ensures stability, prevents regressions, makes refactoring safe, and enforces solid clean architecture principles in our codebase.

**Goals:**
- Provide clear guidelines on what to test and how to test it.
- Introduce categorized levels of testing (Unit, Integration, UI).
- Define testing toolchains compatible with modern Android development (Kotlin, Coroutines, Jetpack Compose, Koin).
- Suggest a phased rollout strategy for introducing tests step-by-step.

---

## 2. Current State

Based on the project structure:
- **Architecture Overview**: The app follows a clear separation of concerns with `data/`, `domain/`, `presentation/`, and `ui/` directories.
- **Dependency Injection**: Uses `Koin`.
- **UI Framework**: Uses Jetpack Compose heavily (`ui/screens`, `ui/components`).
- **Existing Tests**: Minimal generated tests exist (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`).
- **Dependencies Installed**: JUnit, AndroidX JUnit, Espresso Core, Compose UI Test.

---

## 3. Recommended Testing Ecosystem

To effectively test this modern stack, we need to introduce a few specific testing dependencies in `app/build.gradle.kts`:

### **Essential Libraries to Add:**
1. **MockK**: The absolute best mocking library for Kotlin (`io.mockk:mockk`, `io.mockk:mockk-android`).
2. **KotlinX Coroutines Test**: For testing suspend functions and Coroutine Dispatchers (`org.jetbrains.kotlinx:kotlinx-coroutines-test`).
3. **Turbine**: A small testing library for Kotlin Flows, essential for ViewModel and Repository testing (`app.cash.turbine:turbine`).
4. **Koin Test**: For testing DI configurations (`io.insert-koin:koin-test`, `io.insert-koin:koin-test-junit4`).

---

## 4. Testing Layers

We will adopt a Testing Pyramid tailored for Android applications:

### A. Unit Tests (`src/test/`)
**Goal:** Test isolated, pure Kotlin classes, logic, algorithms, and business rules without the Android Framework.
**Speed:** Extremely fast.
**Scope:**
- **Domain Layer (`domain/`)**: Ensure use cases and pure business logic output correctly for various inputs. Mock repositories.
- **Data Layer (`data/`)**: Test data mapping, JSON serialization, and repository caching logic. Mock APIs, databases, or content providers (using MockK).
- **Presentation Layer (`presentation/`)**: Test `ViewModel` state exposure, StateFlows, and user intent handling using MockK and Turbine.
- **Utils (`util/`)**: Unit test pure utility functions comprehensively (e.g., `PermissionUtil`). 

### B. Integration Tests (`src/test/` or `src/androidTest/`)
**Goal:** Verify that units work together (e.g., ViewModel + Repository + Local DB).
**Scope:**
- **Koin Modules**: Write tests ensuring the DI graphs are structurally whole (`checkModules()`).
- **Data Integrations**: Tests with an in-memory Room DB (if any) or shared preferences storage (DataStore) to assert data flows properly through the repositories.

### C. UI Component Tests (`src/androidTest/` or Robolectric)
**Goal:** Verify individual Compose elements independently of their screens.
**Speed:** Moderate.
**Scope:**
- **Compose UI Tests**: Use `createComposeRule()` to test standard UI components in isolation (`AppIcon`, `SearchResultItems`, etc.).
- **Checks**: Verify proper visual rendering, correct content description usage (Accessibility!), and simple user interactions (clicks, gestures).

### D. End-to-End (E2E) UI Tests (`src/androidTest/`)
**Goal:** Verify the app from end-to-end as a user would experience it. 
**Speed:** Slowest.
**Scope:**
- Launch `MainActivity` or `SettingsActivity`.
- Perform user flows: E.g., Open Launcher -> Type in search -> Tap on result -> Expect action intent fired.
- Modify preferences -> Open settings -> Verify toggles -> Observe system changes.
- **Frameworks**: Compose UI Test combined with Espresso. Use UI Automator if interacting with the notification shade or moving between apps.

---

## 5. Implementation Roadmap (Phases)

Rolling out comprehensive tests should be iterative. Do not try to write tests for everything at once.

### Phase 1: Infrastructure and Core Utilities
- Update `build.gradle.kts` with `MockK`, `Coroutines Test`, and `Turbine`.
- Setup a basic CI/CD pipeline (e.g., GitHub Actions) to run `./gradlew testDebugUnitTest` and check linting on Push/PR.
- **Action Items**: Write unit tests for the `util/` directory to ensure foundational functions work correctly.

### Phase 2: Domain Layer (The Core)
- Focus entirely on `src/test/`.
- Target the `domain/` package.
- Write tests for Use Cases, passing in mocked Repositories.
- Target 100% coverage for the Domain layer since it should be pure Kotlin and easy to test.

### Phase 3: Data & ViewModels (The Middlemen)
- Start using **Turbine** to test StateFlows in ViewModels found in the `presentation/` package.
- Assert that ViewModels emit the correct exact sequence of Loading -> Success / Error states given different Mock repository behaviors.
- Target Repositories inside `data/` mapping test responses.

### Phase 4: Jetpack Compose Component Tests
- Move to `src/androidTest/`.
- Identify isolated, reusable components (e.g., `SettingsScreen`, `AppIcon`, search bars).
- Write UI tests asserting that they react to inputs (state hoisting) and display the correct information given specific dummy states.

### Phase 5: E2E and DI Tests
- Add a `KoinTest` class to verify all Dependency Injection modules load without crashing.
- Write 1 or 2 critical E2E UI Tests covering the primary "Happy Path" of the launcher (e.g., Search for an app & launch it).

---

## 6. Testing Best Practices and Conventions

1. **Test Driven Development (TDD)**: Slowly encourage writing a failing test *before* fixing a bug, or when implementing new domain logic.
2. **Naming Convention**: 
   - Class Names: `ModuleNameTest` (e.g., `AppRepositoryImplTest`)
   - Test Names: Use clear readable descriptions, generally leveraging backticks in Kotlin:
     ```kotlin
     @Test
     fun `given empty search query, when submitting, then emit EmptyState`() { ... }
     ```
3. **Structure**: Follow `Given-When-Then` (or `Arrange-Act-Assert`).
   ```kotlin
   // Given (Arrange)
   val fakeData = listOf(AppModel(...))
   coEvery { mockRepository.getApps() } returns fakeData

   // When (Act)
   viewModel.loadApps()

   // Then (Assert)
   viewModel.uiState.test {
       assertEquals(UiState.Success(fakeData), awaitItem())
   }
   ```
4. **Avoid Flakiness**: 
   - Never use `Thread.sleep()`. Use Compose's idling resources / testing clocks. 
   - Ensure Coroutines are properly injecting standard TestDispatchers instead of hardcoding `Dispatchers.IO` inside the logic classes.

---

## 7. Next Actions

To begin, we should update the `build.gradle.kts` dependencies to include the necessary libraries mentioned in Phase 1, followed by adding a minimal test suite for the easiest targets like utility functions or View Models.

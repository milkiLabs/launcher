# Milki Launcher

A custom Android Launcher app built with modern Android development practices. Features a searchable app drawer with multi-mode search capabilities.

## Features

- **Multi-Mode Search**: Search apps, contacts, web, and YouTube
  - No prefix: Search installed apps
  - `s `: Web search
  - `c `: Contacts search
  - `y `: YouTube search

- **Smart Layout**: Grid for apps, list for provider results
- **Recent Apps**: Saves 8 most recently used apps
- **Material Design 3**: Modern UI with dynamic colors support
- **Performance Optimized**: O(n) search, controlled parallelism, memory caching

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Architecture**: MVVM + Clean Architecture
- **Image Loading**: Coil
- **Data**: DataStore Preferences
- **Async**: Kotlin Coroutines + Flow

## Documentation

Comprehensive documentation is available in the [docs/](docs/) directory:

- **[Documentation Index](docs/README.md)** - Entry point for all project docs
- **[Architecture](docs/Architecture.md)** - Current package map, layer boundaries, and runtime flow
- **[Conventions](docs/Conventions.md)** - Placement rules, naming standards, and engineering guardrails
- **[Contributing](docs/Contributing.md)** - New-contributor setup, workflow, and review checklist
- **[Distilled Learnings](docs/Distilled-Learnings.md)** - Hard-earned lessons from refactors and production-adjacent fixes

## Quick Start

1. Open in Android Studio
2. Sync Gradle files
3. Run on device/emulator (API 24+)
4. Set as default launcher when prompted

## Requirements

- Minimum SDK: API 24 (Android 7.0)
- Target/Compile SDK: API 36 (Android 16)
- JDK: 11 or higher

## License

Educational project - feel free to learn from and modify!

---

## Feature Roadmap

### Planned Features

- [ ] Long-press actions on apps (info, uninstall, split screen)
- [ ] Home screen gestures (swipe up for search, double-tap to lock)
- [ ] Custom user macros/automation
- [ ] Fuzzy matching for app names
- [ ] Prefix localization (Arabic: س, ي, ت)
- change empty state for search,youtube

### Performance Notes

- Current search is O(n) - suitable for ~200 apps
- Future additions (contacts, shortcuts, settings) may require indexing
- Need to implement lifecycle handling for app install/uninstall/update

---

See the [`docs/`](docs/) folder for detailed documentation on architecture, components, and features.

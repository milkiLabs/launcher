# Milki Launcher

A custom Android Launcher app built with modern Android development practices. Features a searchable app drawer with multi-mode search capabilities.

## Features

- **Multi-Mode Search**: Search apps, contacts, web, and YouTube
  - No prefix: Search installed apps
  - `s `: Web search
  - `c `: Contacts search
  - `y `: YouTube search

- **Smart Layout**: Grid for apps, list for provider results
- **Recent Apps**: Saves 5 most recently used apps
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

Comprehensive documentation is available in the [`docs/`](docs/) folder:

- **[Architecture Guide](docs/Architecture.md)** - Complete architecture documentation
- **[Multi-Mode Search](docs/multi-mode-search.md)** - Search feature details
- **[AppIconFetcher](docs/AppIconFetcher.md)** - Custom icon loading
- **[Build Configuration](docs/BuildConfiguration.md)** - Gradle setup
- **[AndroidManifest](docs/AndroidManifest.md)** - App configuration
- **[Theme](docs/Theme.md)** - Material 3 theming
- **[LauncherApplication](docs/LauncherApplication.md)** - App initialization

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
- [ ] File search with `f ` prefix
- [ ] Custom user macros/automation
- [ ] Fuzzy matching for app names
- [ ] Prefix localization (Arabic: س, ي, ت)

### Performance Notes
- Current search is O(n) - suitable for ~200 apps
- Future additions (contacts, shortcuts, settings) may require indexing
- Need to implement lifecycle handling for app install/uninstall/update

---

See the [`docs/`](docs/) folder for detailed documentation on architecture, components, and features.

# Prefix Configuration Feature

## Overview

The prefix configuration feature allows users to customize the prefixes used to trigger search providers in Milki Launcher. This is especially useful for:

1. **Multilingual users**: Users can add prefixes in their native language without switching keyboards
2. **Custom preferences**: Users can change default prefixes to their preferred shortcuts
3. **Accessibility**: Users can choose prefixes that are easier to type

## Key Features

### Multiple Prefixes Per Provider

Each search provider can have multiple prefixes. For example:
- Files search: `f`, `م` (Arabic), `find`
- Web search: `s`, `ج` (Arabic), `web`
- YouTube search: `y`, `yt`, `tube`

### Prefix Length

Prefixes can be:
- **Single character**: `f`, `c`, `y`, `s`
- **Multiple characters**: `find`, `web`, `tube`, `yt`
- **Unicode characters**: `م`, `ج`, `ف` (Arabic letters)

### How Prefix Activation Works

A prefix only activates when followed by a space:
- `f report.pdf` → Triggers files search for "report.pdf"
- `f` without space → Searches apps starting with "f"

This prevents accidental triggering while typing app names.

## Architecture

### Data Model

```
┌─────────────────────────────────────────────────────────────┐
│                    PrefixConfiguration                       │
│  Map<ProviderId, PrefixConfig>                              │
│                                                              │
│  Example:                                                    │
│  {                                                          │
│    "web" -> PrefixConfig(["s", "ج"]),      // Arabic 'ج'    │
│    "files" -> PrefixConfig(["f", "م"]),    // Arabic 'م'    │
│    "contacts" -> PrefixConfig(["c"]),                       │
│    "youtube" -> PrefixConfig(["y", "yt"])                   │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `PrefixConfig.kt` | Data model for prefix configuration |
| `LauncherSettings.kt` | Contains `prefixConfigurations` map |
| `SettingsRepositoryImpl.kt` | Persists prefix configs to DataStore |
| `SearchProviderConfig.kt` | Added `providerId` field |
| `SearchProviderRegistry.kt` | Manages prefix-to-provider mappings |
| `QueryParser.kt` | Parses queries using configured prefixes |
| `SettingsViewModel.kt` | Prefix management methods |
| `SettingsComponents.kt` | UI components for prefix configuration |
| `SettingsScreen.kt` | Prefix configuration UI section |

### Data Flow

```
User changes prefix in Settings UI
        ↓
SettingsViewModel.addProviderPrefix()
        ↓
LauncherSettings.prefixConfigurations updated
        ↓
SettingsRepositoryImpl persists to DataStore (JSON format)
        ↓
SearchViewModel observes settings changes
        ↓
SearchProviderRegistry.updatePrefixConfigurations()
        ↓
QueryParser uses updated prefixes
```

### Storage Format

Prefix configurations are stored in DataStore as a JSON string:

```json
{
  "web": ["s", "ج"],
  "files": ["f", "م", "find"],
  "contacts": ["c"],
  "youtube": ["y", "yt"]
}
```

## Provider IDs

Provider IDs are stable identifiers used as keys in the configuration map:

| Provider | ID | Default Prefix |
|----------|-----|----------------|
| Web Search | `web` | `s` |
| Contacts | `contacts` | `c` |
| YouTube | `youtube` | `y` |
| Files | `files` | `f` |

**Important**: Provider IDs must remain stable across app versions as they are stored in user preferences.

## Usage Examples

### Adding a Prefix

```kotlin
// In SettingsViewModel
fun addProviderPrefix(providerId: String, prefix: String) {
    updateSetting { settings ->
        val currentPrefixes = settings.prefixConfigurations[providerId]?.prefixes
            ?: listOf(getDefaultPrefix(providerId))
        
        if (prefix !in currentPrefixes) {
            val newConfigurations = settings.prefixConfigurations.toMutableMap()
            newConfigurations[providerId] = PrefixConfig(currentPrefixes + prefix)
            settings.copy(prefixConfigurations = newConfigurations)
        } else {
            settings
        }
    }
}
```

### Query Parsing

```kotlin
// In QueryParser.kt
fun parseSearchQuery(input: String, registry: SearchProviderRegistry): ParsedQuery {
    val allPrefixes = registry.getAllPrefixes()
    val sortedPrefixes = allPrefixes.sortedByDescending { it.length }
    
    for (prefix in sortedPrefixes) {
        val prefixWithSpace = prefix + " "
        if (input.startsWith(prefixWithSpace)) {
            val provider = registry.findByPrefix(prefix)
            if (provider != null) {
                val query = input.substring(prefixWithSpace.length)
                return ParsedQuery(provider, query, provider.config)
            }
        }
    }
    // ... fallback to app search
}
```

## UI Components

### PrefixSettingItem

A setting item that displays:
- Provider name and icon
- Current prefixes as chips
- Add button for new prefixes
- Reset button to restore defaults

### PrefixChip

A chip displaying a single prefix with a remove button.

### AddPrefixDialog

A dialog for adding a new prefix with validation:
- Empty prefix check
- Space check (prefixes cannot contain spaces)
- Duplicate check (prefix must not already exist)

## Testing Considerations

When testing prefix configuration:

1. **Prefix collision**: Ensure different providers don't have the same prefix
2. **Unicode support**: Test Arabic, Chinese, and other non-Latin characters
3. **Multi-character prefixes**: Ensure longer prefixes match before shorter ones
4. **Persistence**: Verify settings persist across app restarts
5. **Reset functionality**: Verify reset restores default prefixes

## Future Enhancements

Potential improvements:

1. **Prefix conflict detection**: Warn when adding a prefix that another provider uses
2. **Import/Export**: Allow users to share prefix configurations
3. **Preset configurations**: Provide preset configurations for different languages
4. **Voice input**: Consider how prefixes work with voice search

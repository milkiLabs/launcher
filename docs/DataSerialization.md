# Data Serialization

This document explains how data is serialized and persisted in the launcher app.

## Overview

The launcher uses **kotlinx.serialization** for JSON serialization of data models. This provides:

- **Type-safe serialization**: Compile-time verification of serializable types
- **Polymorphic serialization**: Sealed classes are serialized with type discriminators
- **No delimiter collision**: Unlike pipe-delimited formats, JSON handles any character in values

## Configuration

### Gradle Setup

The serialization plugin and dependency are configured in:

1. **gradle/libs.versions.toml** - Version definitions:
```toml
[versions]
kotlinxSerializationJson = "1.7.3"

[libraries]
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

2. **build.gradle.kts** (project level) - Plugin declaration:
```kotlin
plugins {
    alias(libs.plugins.kotlin.serialization) apply false
}
```

3. **app/build.gradle.kts** (app level) - Plugin application and dependency:
```kotlin
plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
```

## Usage

### Marking Classes as Serializable

Add the `@Serializable` annotation to data classes:

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class GridPosition(
    val row: Int,
    val column: Int
)
```

### Sealed Class Serialization

Sealed classes require the `@Serializable` annotation on both the base class and all subclasses:

```kotlin
@Serializable
sealed class HomeItem {
    abstract val id: String
    abstract val position: GridPosition
    
    @Serializable
    data class PinnedApp(
        override val id: String,
        val packageName: String,
        val activityName: String,
        val label: String,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem()
    
    @Serializable
    data class PinnedFile(
        override val id: String,
        val uri: String,
        val name: String,
        val mimeType: String,
        val size: Long = 0,
        override val position: GridPosition = GridPosition.DEFAULT
    ) : HomeItem()
}
```

### JSON Configuration

The `Json` instance is configured for serialization:

```kotlin
val json: Json = Json {
    // Field name used to identify sealed class subclasses
    classDiscriminator = "type"
    
    // Ensures default values are written to JSON
    encodeDefaults = true
}
```

### Serializing to JSON

```kotlin
// Encode an object to JSON string
val item = HomeItem.PinnedApp(
    id = "app:com.example/.MainActivity",
    packageName = "com.example",
    activityName = ".MainActivity",
    label = "Example App",
    position = GridPosition(0, 1)
)
val jsonString: String = json.encodeToString(item)
```

### Deserializing from JSON

```kotlin
// Decode a JSON string to an object
val decodedItem: HomeItem = json.decodeFromString<HomeItem>(jsonString)
```

## JSON Output Format

### Simple Data Class

```kotlin
GridPosition(2, 3)
```

Serializes to:
```json
{"row":2,"column":3}
```

### Sealed Class (Polymorphic)

```kotlin
HomeItem.PinnedApp(
    id = "app:com.whatsapp/.Main",
    packageName = "com.whatsapp",
    activityName = ".Main",
    label = "WhatsApp",
    position = GridPosition(0, 1)
)
```

Serializes to:
```json
{
    "type": "PinnedApp",
    "id": "app:com.whatsapp/.Main",
    "packageName": "com.whatsapp",
    "activityName": ".Main",
    "label": "WhatsApp",
    "position": {"row": 0, "column": 1}
}
```

The `"type"` field is automatically added by the polymorphic serializer to identify the subclass.

## Storage in DataStore

Items are stored in DataStore as newline-separated JSON:

```
{"type":"PinnedApp","id":"app:com.whatsapp/.Main",...}
{"type":"PinnedFile","id":"file:content://...","name":"Report.pdf",...}
```

Each line is a complete, parseable JSON object. This format:

- Preserves order (unlike StringSet)
- Allows easy addition/removal of items
- Handles special characters in values correctly

## Related Files

- `domain/model/HomeItem.kt` - Serializable data models
- `domain/model/GridPosition.kt` - Position model
- `data/repository/HomeRepositoryImpl.kt` - Serialization usage
- `gradle/libs.versions.toml` - Version configuration

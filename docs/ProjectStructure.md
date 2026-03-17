```
launcher/
├── settings.gradle.kts          # Project settings
├── build.gradle.kts             # Project-level build config
├── gradle.properties            # Gradle properties
├── gradle/libs.versions.toml    # Version catalog
└── app/
    └── build.gradle.kts         # App-level build config
```

// ============================================================================
// BUILD STRUCTURE
// ============================================================================
// Typical Android project structure:
//
// root/
// ├── build.gradle.kts (this file - project-level config)
// ├── settings.gradle.kts (project settings, modules list)
// ├── gradle.properties (Gradle properties)
// ├── gradle/libs.versions.toml (version catalog - dependency versions)
// └── app/
// └── build.gradle.kts (app module config - dependencies, SDK versions)
//
// For multi-module projects:
// root/
// ├── build.gradle.kts
// ├── settings.gradle.kts
// ├── app/build.gradle.kts
// ├── library/build.gradle.kts
// ├── feature1/build.gradle.kts
// └── feature2/build.gradle.kts

---

keystore.properties file.
// This file is kept outside version control for security (contains passwords).
// Location: project root directory (same level as settings.gradle.kts)
// The properties file contains:
// - storeFile: Path to the .jks keystore file
// - storePassword: Password for the keystore
// - keyAlias: Name of the key inside the keystore
// - keyPassword: Password for the specific key

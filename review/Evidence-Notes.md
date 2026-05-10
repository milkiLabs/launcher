# Evidence Notes and Confidence

## Confirmed directly in source
- Core -> presentation coupling in permission handling:
  - `app/src/main/java/com/milki/launcher/core/permission/PermissionHandler.kt:30`
- Duplicated launch intent builder:
  - `app/src/main/java/com/milki/launcher/data/repository/apps/InstalledAppsCatalog.kt:60`
  - `app/src/main/java/com/milki/launcher/data/repository/apps/RecentAppsStore.kt:120`
- Duplicated suggestion parsing logic families:
  - `app/src/main/java/com/milki/launcher/domain/search/ClipboardSuggestionResolver.kt:94`
  - `app/src/main/java/com/milki/launcher/domain/search/QuerySuggestionResolver.kt:126`
- Deprecated callback usage:
  - `app/src/main/java/com/milki/launcher/app/activity/MainActivity.kt:101`
- Minification disabled in release:
  - `app/build.gradle.kts:43`
- Storage and backup posture:
  - `app/src/main/AndroidManifest.xml:18`
  - `app/src/main/AndroidManifest.xml:43`
- Dead placeholder object:
  - `app/src/main/java/com/milki/launcher/data/search/SearchProviderUtils.kt:31`
- Unused parameter suppression:
  - `app/src/main/java/com/milki/launcher/ui/components/search/SearchResultUrlItem.kt:23`

## Quantitative checks
- Broad exception catches in main source: 32 instances (regex-based count).
- Approximate test file count: 15.
- Approximate main Kotlin file count: 185.

## False-positive correction from subagent output
- Claimed duplicate file `app/src/main/java/com/milki/launcher/util/AppLauncher.kt` was not found in current workspace.
- Audit conclusions were adjusted to avoid relying on that claim.

## Items requiring profiling/validation before refactor
- Mutation lock throughput and user-visible latency under drag stress.
- Compose recomposition hotspot severity for draggable grid logic.
- Widget ID leakage behavior on binding/configuration failure paths.

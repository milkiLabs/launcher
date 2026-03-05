# UX/UI and Design System Audit

## P0-P1 Design System Findings

### 1) Spacing token policy is violated with hardcoded `dp`
- Evidence: `app/src/main/java/com/milki/launcher/ui/components/FolderPopupDialog.kt:371`, `app/src/main/java/com/milki/launcher/ui/components/FolderPopupDialog.kt:423`
- Problem: Direct `360.dp` and `340.dp` are used instead of centralized tokens.
- Impact: Inconsistent sizing semantics and harder global tuning.
- Recommendation:
1. Add explicit size tokens in `Spacing.kt` (for dialog max width/height semantics).
2. Replace hardcoded values with those tokens.

### 2) Icon directionality standard is not consistently followed
- Evidence examples:
- `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:355`
- `app/src/main/java/com/milki/launcher/ui/components/ItemActionMenu.kt:138`
- `app/src/main/java/com/milki/launcher/ui/components/SearchResultContactItem.kt:54`
- Problem: Widespread use of `Icons.Filled` / `Icons.Default` where `AutoMirrored` variants should be used when available.
- Impact: RTL behavior inconsistency and standard drift.
- Recommendation:
1. Introduce lint/checklist rule: prefer `Icons.AutoMirrored.*` when available.
2. Perform one focused migration PR for all icon callsites.

## UX Interaction Findings

### 3) Homescreen menu anchor can be off-screen
- Evidence: `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:341`
- Problem: `DropdownMenu` offset is derived directly from raw touch position, without clamping to viewport.
- Impact: Menu may render partially off-screen on edge touches.
- Recommendation:
1. Clamp anchor to safe bounds (window size - menu estimated size).
2. Add fallback to centered/bottom anchor if clamping fails.

### 4) Error communication is mostly toast-only and non-actionable
- Evidence: many toasts in `app/src/main/java/com/milki/launcher/presentation/search/ActionExecutor.kt` (e.g., `:142`, `:200`, `:215`, `:325`)
- Problem: ephemeral feedback without direct recovery action.
- Impact: Poor guidance for permission/app-missing failures.
- Recommendation:
1. Standardize error surface model (toast/snackbar/dialog based on severity).
2. Provide action buttons for recoverable failures (open settings, retry, choose app).

### 5) Placeholder shortcut support degrades trust
- Evidence: `app/src/main/java/com/milki/launcher/ui/screens/LauncherScreen.kt:526`, `app/src/main/java/com/milki/launcher/util/AppLauncher.kt:155`, `app/src/main/java/com/milki/launcher/ui/components/PinnedItem.kt:524`
- Problem: Shortcut launching/icon loading has known TODO gaps.
- Impact: Incomplete feature quality in a core launcher flow.
- Recommendation:
1. Complete launcher shortcut API integration.
2. Add clear disabled state if full support is not yet implemented.

## Accessibility and Usability Suggestions

1. Add content descriptions for key action icons where currently null unless decorative.
2. Add minimum touch-target checks for contextual actions in dense overlays.
3. Add stronger contrast strategy for text over transparent wallpaper backgrounds.
4. Ensure keyboard/back behavior is predictable across overlay stack (search, folder, drawer, widget picker).

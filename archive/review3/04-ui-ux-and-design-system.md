# UX/UI & Design System Audit

## 1) Design-system violations: hardcoded spacing values (**P1**)
### Evidence
- Multiple `20.dp`, `24.dp`, `1.dp`, `0.dp` in `SettingsComponents.kt` and other UI files.

### Impact
- Inconsistent spacing scale and harder global tuning.

### Fix
- Replace all layout spacing/sizing with `Spacing`, `IconSize`, `CornerRadius` tokens.
- If a needed token is missing, add it centrally first.

---

## 2) Material icon policy not consistently followed (**P1**)
### Evidence
- Extensive `Icons.Default.*` usage where AutoMirrored alternatives exist (`SearchResultItems`, `SettingsScreen`, provider classes).

### Impact
- RTL behavior can be inconsistent.

### Fix
- Audit all directional icons and migrate to `Icons.AutoMirrored.*` where applicable.

---

## 3) Hardcoded colors outside theme primitives (**P1**)
### Evidence
- Provider colors and file icon colors hardcoded in feature files.

### Impact
- Theme inconsistency and poor dark-mode/control cohesion.

### Fix
- Move to theme tokens (or semantic color mapping by provider/file type in theme layer).

---

## 4) Settings UX exposes controls that don’t work (**P0/P1 UX trust issue**)
### Evidence
- Many controls change stored values without runtime effect.

### Impact
- Perceived app quality drops sharply; users assume bugs.

### Fix
- Hide unfinished controls behind feature flags OR fully wire behavior.
- Add “Coming soon” only if intentionally deferred.

---

## 5) URL result secondary action missing despite API parameter (**P2**)
### Evidence
- `UrlSearchResultItem` has `onOpenInBrowser` parameter but TODO states secondary action not implemented.

### Impact
- Users cannot easily override deep-link default from result row.

### Fix
- Add explicit trailing overflow/secondary button for “Open in browser”.

---

## 6) Settings entrypoint flow is awkward (**P1**)
### Evidence
- Separate launcher icon for settings activity.

### Impact
- User mental model confusion for launcher shell.

### Fix
- Move settings access to launcher menu/long-press action, keep one launcher icon entry.

---

## 7) Empty-state copy not settings-aware (**P2**)
### Evidence
- Prefix hint text and provider labels are hardcoded around defaults.

### Impact
- If custom prefixes are configured, UI hints become wrong.

### Fix
- Build hint text dynamically from active registry/configuration.

---

## 8) Interaction consistency opportunities (**P2**)
### Evidence
- Mixed tap/long-press/drag handling across components with partially duplicated menu gesture logic.

### Fix
- Define canonical interaction contracts per surface:
  - Search list item
  - Home grid item
  - External drag source
- Keep one shared behavior matrix and haptic policy.
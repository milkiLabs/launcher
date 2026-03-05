# Folders Feature Audit

Date: 2026-03-06
Scope: folder creation, merge, move, extract, rename, popup lifecycle, and folder persistence behavior.

## Findings

### P1) Folder domain logic is correct but concentrated in a very large persistence class
- Evidence: `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:431`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:479`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:613`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:704`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:813`
- Problem: Most folder invariants and mutation rules live inside `HomeRepositoryImpl` (~1186 lines total), mixed with non-folder responsibilities.
- Risk: High regression chance when touching any folder path, hard onboarding/testing.
- Recommendation:
1. Extract folder mutation engine into dedicated class (`FolderMutationEngine`).
2. Keep repository as orchestration + storage adapter.
3. Add contract tests for each mutation path (create/add/merge/extract/move).

### P1) Folder operations pay full deserialize/serialize cost per mutation
- Evidence: repeated full-list edits in `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:451`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:491`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:620`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:712`, `app/src/main/java/com/milki/launcher/data/repository/HomeRepositoryImpl.kt:824`
- Problem: Every folder action parses and rewrites the whole pinned-items payload.
- Risk: Performance degradation as folder/widget counts increase.
- Recommendation:
1. Move to typed schema or incremental mutation representation.
2. Introduce focused folder-storage helpers to avoid repeated full object graph rewrites.

### P2) Folder popup close behavior is distributed across multiple paths
- Evidence: `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:358`, `app/src/main/java/com/milki/launcher/presentation/home/HomeViewModel.kt:602`, `app/src/main/java/com/milki/launcher/MainActivity.kt:587`
- Problem: popup closure is triggered from multiple layers (ViewModel explicit close, extraction close path, activity stop).
- Risk: Future behavior drift and subtle UX inconsistencies.
- Recommendation:
1. Centralize folder popup lifecycle policy in one controller/service.
2. Keep mutation methods pure and emit explicit UI events for popup behavior.

## What Is Good

1. Clear guards for no-nesting/no-widget-in-folder rules.
2. Atomic folder-child extraction/merge APIs exist and prevent naive collision bugs.
3. ViewModel serializes home mutations via mutex, reducing write interleaving bugs.

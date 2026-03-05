# Custom Source Search (Unified External Search)

This document explains the current search-source design after the cleanup refactor.

## Goal

Replace legacy hardcoded external providers (web + YouTube toggles and default engine) with one unified model:

- External search is fully source-driven (`SearchSource` list in settings)
- Local providers remain explicit toggles (`contacts`, `files`)
- No-prefix query mode stays app-only
- Plain-query external suggestions are removed

## What Changed

## 1) Single external source model

External providers are generated at runtime from `LauncherSettings.searchSources`.

Each source defines:

- stable id
- display name
- URL template (`{query}` placeholder required)
- one or more prefixes
- enabled flag
- accent color hex

Runtime registration happens in `SearchViewModelSettingsAdapter` by creating `ConfigurableUrlSearchProvider` for each enabled source.

## 2) Legacy settings removed from active model

The following are no longer part of `LauncherSettings` and no longer have repository/viewmodel/screen APIs:

- default web search engine
- web search enabled toggle
- youtube search enabled toggle

Why: these concepts duplicate the source model and create conflicting state.

## 3) No-prefix behavior simplified

No prefix means app search only.

- app results use the app grid behavior
- external source execution requires prefix mode
- plain-query source suggestion pipeline is removed

## 4) Clipboard suggestion behavior

Clipboard chip still supports typed actions (URL, phone, email, map, text).

For text suggestions, the action is an explicit browser search action label and callback (`onSearchTextInBrowser`), without default-engine state.

## Data Notes

`SettingsRepositoryImpl` persists source configuration directly through `search_sources`.

The canonical model is now source-first without legacy bridge fields.

## Settings UX

Settings now separates concerns clearly:

- Search Providers section: local providers only (`contacts`, `files`)
- Custom Sources section: add/edit/delete/enable/disable prefixes + color for external sources
- Local Prefixes section: local provider prefix customization only

This removes duplicated controls and keeps one obvious place to manage external sources.

## Removed Dead Code

Legacy hardcoded provider implementations were removed:

- `data/search/WebSearchProvider.kt`
- `data/search/YouTubeSearchProvider.kt`

DI registrations for these classes were also removed from `AppModule`.

## Why This Design Is Cleaner

- one source of truth for external providers
- fewer conflicting settings
- less synchronization/bridge code
- simpler mental model for users and for new contributors
- easier future extensibility (new source = data entry, not new provider class)

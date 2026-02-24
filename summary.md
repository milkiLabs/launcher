# Source Files Summary

| Category         | File                                              | Purpose                                                                |
| ---------------- | ------------------------------------------------- | ---------------------------------------------------------------------- |
| Application      | LauncherApplication.kt                            | Custom Application class, DI container holder, Coil ImageLoader config |
| Activities       | MainActivity.kt                                   | Main launcher home screen                                              |
|                  | SettingsActivity.kt                               | Settings screen                                                        |
| ViewModels       | SearchViewModel.kt                                | Search state management, coordinates search providers                  |
|                  | SettingsViewModel.kt                              | Settings state management                                              |
| DI               | AppContainer.kt                                   | Manual DI container with lazy singletons and ViewModel factories       |
| Repositories     | AppRepository.kt / AppRepositoryImpl.kt           | Installed apps, recent apps                                            |
|                  | ContactsRepository.kt / ContactsRepositoryImpl.kt | Contacts access, recent contacts                                       |
|                  | FilesRepository.kt / FilesRepositoryImpl.kt       | Document file search                                                   |
|                  | SettingsRepository.kt / SettingsRepositoryImpl.kt | Settings persistence                                                   |
| Search Providers | WebSearchProvider.kt                              | "s" prefix - web search                                                |
|                  | ContactsSearchProvider.kt                         | "c" prefix - contacts search                                           |
|                  | FilesSearchProvider.kt                            | "f" prefix - files search                                              |
|                  | YouTubeSearchProvider.kt                          | "y" prefix - YouTube search                                            |
| Domain           | SearchProviderRegistry.kt                         | Registry pattern for providers                                         |
|                  | FilterAppsUseCase.kt                              | App filtering logic                                                    |
|                  | UrlHandlerResolver.kt                             | URL deep link resolution                                               |
|                  | QueryParser.kt                                    | Search query parsing                                                   |
| Presentation     | ActionExecutor.kt                                 | Executes search result actions                                         |
|                  | SearchResultAction.kt                             | Sealed class for action types                                          |
|                  | SearchUiState.kt                                  | UI state data class                                                    |
|                  | LocalSearchActionHandler.kt                       | CompositionLocal for action handling                                   |
| Models           | AppInfo.kt                                        | Installed app data                                                     |
|                  | Contact.kt                                        | Contact data                                                           |
|                  | FileDocument.kt                                   | File data                                                              |
|                  | LauncherSettings.kt                               | Settings data                                                          |
|                  | SearchResult.kt                                   | Sealed class for result types                                          |
|                  | SearchProviderConfig.kt                           | Provider config data                                                   |
| Utilities        | PermissionHandler.kt                              | Permission management                                                  |
|                  | PermissionUtil.kt                                 | Permission checking utilities                                          |
|                  | MimeTypeUtil.kt                                   | MIME type utilities                                                    |
| Coil             | AppIconFetcher.kt                                 | Custom Coil fetcher for app icons                                      |
| UI               | Various Compose files                             | UI components, screens, theme                                          |

# Search Dialog

The Search Dialog is the command center of Milki Launcher. It is where you search for apps, files, contacts, execute custom actions, and get smart suggestions—all from a single input.

## The Omnibar(should you call it search dialog or omnibar?)

The core of Milki is the Omnibar—a central search input that handles almost everything.

- **App Search:** Type part of an app name or even its initials to instantly launch it.
- **Web Search:** Directly search the web using your preferred search engine.
- **Quick Actions:** Execute built-in commands directly from the search bar (e.g., `/settings`, `/wifi`).

## Recent Apps

When you open the Search Dialog, it immediately shows your recently accessed apps for quick return.

- Pressing Enter opens the latest app
- Recent apps are those opened from the drawer or search dialog. Apps launched from home screen icons are not added to recent apps since they are already one tap away.

> [!TIP] Placeholder: Screenshot
> Add a screenshot of the Search Dialog opening with recently accessed apps displayed at the top.

## Clipboard Actions

Milki automatically detects content on your clipboard and surfaces relevant suggested actions the moment you open the dialog.

- **Web Search:** If it looks like a query, search the web directly.
- **Open in App:** If it is a link to YouTube, Instagram, or another service, open it in the corresponding installed app.
- **Custom Actions:** You can define your own clipboard action rules in `Settings`.

> [!TIP] Placeholder: GIF
> Show a GIF of copying a YouTube URL, opening the Search Dialog, and tapping the "Open in YouTube" suggestion.

## Searching for Apps

Start typing with no prefix and Milki will search your installed apps. Partial matches and initials both work.

## URL Detection

When you type or paste a URL into the search input, Milki detects it and gives you options:

- **Open in Default Browser**
- **Open in an Installed App** that can handle that URL scheme

> [!TIP] Placeholder: GIF
> Show a GIF of typing a URL into the search bar and a prompt appearing to choose between browser and a specific app.

## System Prefixes

Prefixes let you narrow your search scope or trigger custom actions. Milki ships with two built-in prefixes:

| Prefix | Scope    | Description                                                                                                 |
| ------ | -------- | ----------------------------------------------------------------------------------------------------------- |
| `f`    | Files    | Shows your recent files and lets you search all files on your device                                        |
| `c`    | Contacts | Shows your recent contacts and lets you search all contacts. You can call someone directly from the results |

> [!TIP] Placeholder: Screenshot
> Add a screenshot showing a `c` prefix search with recent contacts at the top and a visible "Call" action button.

## Drag to Home Screen

Any item from the Search Dialog—app, file, or contact—can be dragged directly onto your home screen for quick access.

> [!TIP] Placeholder: GIF
> Show a GIF of searching for an app in the Search Dialog, long-pressing the result, and dragging it onto an empty home screen cell.

## Multiple Prefixes for the Same Target

You can map more than one prefix to the same search target or URL. This is especially useful for bilingual users.

For example, if you use both English and Arabic, you can create two prefixes that both search Wikipedia—one triggered by `w` (for English) and another by `و` (for Arabic). This way you can switch languages naturally without changing any settings mid-search.

> [!TIP] Placeholder: Screenshot
> Add a screenshot of the Settings > Prefixes screen showing two prefixes pointing to the same service but different language endpoints.

## Custom Prefixes

You can create your own prefixes for anything you want to search. Examples:

- Search Wikipedia (`w`)
- Search an image engine (`i`)
- Search a specific website or API

Add and manage custom prefixes in `Settings > Prefixes`. Each prefix maps to a search URL or handler of your choice.

## Color-Coded Prefixes

Every prefix can be assigned a unique color. When you type a prefix into the search dialog, the entire input bar shifts to that prefix's color. This gives you instant visual feedback about which search scope you are in.

> [!TIP] Placeholder: Screenshot
> Add a screenshot showing the search dialog with different prefix colors active side by side, each with a distinct tinted input bar.

> [!TIP] Placeholder: Screenshot
> Add a screenshot of the Settings > Prefixes screen showing the color picker next to each prefix.

> [!TIP] Placeholder: Screenshot
> Add a screenshot of the Settings > Prefixes screen showing existing prefixes and an "Add New Prefix" form.

## Features to add

- include fuzzy finding to filtering
  - Instead of: whatsapp Allow: wa or wsp. should you use a library for this
- Home screen gesturse:
  - Swipe up → open search
  - Double tap → lock screen
  - Swipe down → notifications
  - Long press background → settings
- actions when long pressing an app item from the list:
  - App info
  - Uninstall
  - Open in split screen
- add prefix support: f {file}=> search files, c {contact}=> search contacts. yt {query}=> search youtube
- make user create custom macros and automation. cm => call mom,
- when dialog is open, when clicking back button it closes the keyboard the user has to press back again to close dialog. back button should close dialog in one tap.
- make a prompt to let user set up default launcher.
- contact query should trim white spaces.
- ***

## Features:

- You can search by app name(youtube) or package name(com.google.\*) to show all google packages for example

---

## Performance Improvements

Search algorithm is O(n)

Totally fine for 200 apps.

But if you add:

contacts

shortcuts

settings

commands

You will need indexing or fuzzy search.

Missing lifecycle handling for app changes

Launcher must react to:

install

uninstall

update

You currently don’t listen to package broadcasts.

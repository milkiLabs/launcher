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
- ***

## Features:

- You can search by app name(youtube) or package name(com.google.\*) to show all google packages for example

---

## Performance Improvements

🔹 Cache icons as Bitmap once
Currently:
drawable.toBitmap(48, 48)
That happens on recomposition.
Better:
Convert during loadInstalledApps()
Store as ImageBitmap

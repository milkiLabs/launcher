# Media Capture Workflow

A workflow for creating screenshots and screen recordings from an Android device over ADB, then preparing them for the website.

## Requirements

- Android device with **USB debugging** enabled
- `adb`
- `ffmpeg`
- `ImageMagick`

---

# Background

To keep screenshots consistent across the project, please use `./background.png` as your device wallpaper before taking screenshots or recording videos.

# Screenshots

Capture the current screen directly to your computer:

```bash
adb exec-out screencap -p > screenshot.png
```

No files are written to the phone.

---

# Screen Recording

Record the device:

```bash
adb shell screenrecord /sdcard/demo.mp4
```

Press **Ctrl+C** when finished.

Copy the recording:

```bash
adb pull /sdcard/demo.mp4
```

Remove it from the device:

```bash
adb shell rm /sdcard/demo.mp4
```

---

# Crop Screenshots

Run the crop script inside the folder containing the screenshots:

```bash
./crop_pngs.sh
```

This creates:

```
webp/
    home.webp
    search.webp
    settings.webp
```

The default crop removes:

- **100 px** from the top
- **150 px** from the bottom

Equivalent to:

```
crop=in_w:in_h-250:0:100
```

> **Note:** These values are tuned for my device. You will likely need to adjust them to match your own phone's status bar and navigation/gesture bar. Open a screenshot in GIMP (or another image editor), measure the number of pixels to remove from the top and bottom, then update the `TOP` and `BOTTOM` variables in `crop_pngs.sh`.

---

# Process Videos

Run the video script inside the folder containing the recordings:

```bash
./process_videos.sh
```

This:

- crops the recording using the same `TOP` and `BOTTOM` values as the screenshot script
- removes audio
- encodes the video using **AV1** for excellent compression
- optimizes it for web playback

Output:

```
processed/
    demo.mp4
```

> **Note:** As with screenshots, you may need to adjust the crop values in the script for your device.

---

# Recommended Workflow

1. Connect phone via USB.
2. Open the screen you want to capture.
3. Take all screenshots.
4. Record each feature demonstration.
5. Place screenshots in one folder.
6. Place recordings in another.
7. Run the crop script.
8. Run the video processing script.
9. Copy the outputs into public directory.

---

# Tips

- Keep recordings short (5–15 seconds).
- Navigate slowly and deliberately.
- Disable notifications before recording.
- Use the same device orientation throughout.
- Capture screenshots after recording so they match the final UI.

---

# Optional Aliases

Add these to your shell configuration:

```bash
alias shot='adb exec-out screencap -p'
alias record='adb shell screenrecord /sdcard/demo.mp4'
```

Take a screenshot:

```bash
shot > home.png
```

Start recording:

```bash
record
```

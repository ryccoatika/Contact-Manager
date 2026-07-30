---
name: run-on-device
description: Use when you need to see a UI change actually render (not just compile/test) — build the debug APK, install it on a connected Android device/emulator, launch it, drive it, and capture screenshots via adb. Trigger on "run the app", "screenshot", "does it look right on device", or after any Compose UI change.
---

# Run ContactManager on a device

Unit tests never exercise the UI. To confirm a screen renders correctly, install
and drive the app over `adb`, then Read the screenshots.

## 0. Check a device is attached

```bash
adb devices -l
```

If empty, ask the user to connect a device (USB, debugging on) or start an
emulator. Don't proceed without a `device` line.

## 1. Build + install (grants runtime permissions)

```bash
./gradlew :app:assembleDebug
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```

`-r` reinstalls, `-g` auto-grants runtime permissions (READ/WRITE_CONTACTS,
READ_PHONE_STATE) so you land on the populated contact list, not the permission
gate. If a grant is ever missed:

```bash
for p in READ_CONTACTS WRITE_CONTACTS READ_PHONE_STATE GET_ACCOUNTS; do
  adb shell pm grant com.ryccoatika.contactmanager android.permission.$p
done
```

## 2. Launch

```bash
adb shell monkey -p com.ryccoatika.contactmanager -c android.intent.category.LAUNCHER 1
adb shell sleep 2   # let it settle (device-side sleep; host `sleep` is blocked)
```

## 3. Screenshot → look at it

```bash
adb exec-out screencap -p > /path/to/scratchpad/shot.png
```

Then **Read the PNG** — the whole point is to visually inspect, not assume.

## 4. Drive the UI

`adb shell input` uses **real device pixels** (e.g. 1080×2340). The Read tool
reports the displayed image size and a multiplier — multiply the coordinates you
eyeball back up to device pixels before tapping.

```bash
adb shell input tap <x> <y>
adb shell input swipe <x1> <y1> <x2> <y2> <ms>   # scroll / long-press / drag
adb shell input keyevent KEYCODE_BACK
```

Long-press = a `swipe` with same start/end and a long duration.

## 5. Catch a transient state (shimmer, drag hint, ripple)

Screenshotting a momentary state is racy. Run the gesture in the **background** so
it holds while you capture:

1. Start a slow gesture in the background, e.g. a 2.5s hold on the alphabet rail:
   `adb shell input swipe 1030 1200 1031 1201 2500` (run detached).
2. In a separate call, `adb shell sleep 1` then `screencap` — capturing mid-gesture.

## Notes

- Package / launcher: `com.ryccoatika.contactmanager/.MainActivity`.
- Check the foreground activity: `adb shell dumpsys activity activities | grep -m1 ResumedActivity`.
- Save screenshots to the session scratchpad, not the repo.
- Screens to spot-check after a design change: Home (list, rail, FAB, selection
  bar, move sheet), Detail (hero + account cards), Editor, Accounts (rows,
  capability tags, overflow menu), Duplicates. Check **dark mode** too
  (system dark toggle) since the theme designs both.

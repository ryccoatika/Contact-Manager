---
description: Build, install, launch, and screenshot the app on a connected device to verify a UI change
argument-hint: "[screen or thing to check, e.g. 'accounts screen' or 'search debounce']"
---

Verify the current UI on a connected Android device — this is the **only** time
to device-verify (it is not done automatically after UI edits).

Invoke the **`run-on-device`** skill and follow it end to end:

1. `adb devices -l` — confirm a device is attached; if none, stop and ask the user to connect one.
2. `./gradlew :app:assembleDebug`
3. `adb install -r -g app/build/outputs/apk/debug/app-debug.apk`
4. Launch: `adb shell monkey -p com.ryccoatika.contactmanager -c android.intent.category.LAUNCHER 1`
5. Drive to and screenshot the relevant screen(s), **Read each PNG**, and report what you actually see (light and dark if the change affects both).

Focus of this check: **$ARGUMENTS**

If nothing was specified, screenshot Home and any screen touched by the current diff. Save screenshots to the session scratchpad, not the repo.

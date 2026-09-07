---
description: Audit the app for security issues — secrets, permissions, IPC surface, data handling, dependencies
argument-hint: "[optional focus, e.g. 'manifest' or 'the billing flow']"
---

Run a security audit of this contact-manager app. Contacts are sensitive
personal data and the privacy policy promises **nothing leaves the device**
except anonymous Firebase telemetry — treat any violation of that promise as a
high-severity finding.

Work through every section; report findings as `severity — file:line — issue —
fix`. Verify each finding against the actual code before reporting (no
pattern-matched guesses).

1. **Secrets in the repo/history**
   - `git ls-files | grep -iE '\.jks|\.json|key|secret'` — under `release/`,
     only `.gpg`-encrypted blobs plus `release/app-debug.jks` (deliberately
     committed; well-known public debug credentials) may be tracked. Any other
     plaintext keystore or service-account JSON is a critical finding.
   - Grep the tree for hardcoded tokens/passwords: `grep -rInE "(api[_-]?key|token|password|secret)\s*[:=]" --include="*.kt" --include="*.kts" --include="*.xml" app/ | grep -vi "keystore\|CONTACTMANAGER_RELEASE"` (the debug-keystore `android` credentials are known-public, not a finding).
   - Check `.gitignore` still covers `release/app-release.jks`, `release/*.json`, `local.properties`.

2. **Manifest & IPC surface** (`app/src/main/AndroidManifest.xml`, merged manifest under
   `app/build/intermediates/merged_manifests/` if built)
   - Every `exported="true"` component justified? (Only MainActivity should be.)
   - Permissions: each `uses-permission` still used by code? Anything new pulled
     in by the merged manifest that expands the surface?
   - `allowBackup` / `dataExtractionRules` / `fullBackupContent`: do the rules leak
     DataStore prefs or anything sensitive to cloud backup?

3. **Data leaving the device**
   - Grep network/IO: `grep -rn "http\|URL(\|openConnection\|Socket" app/src/main/java/` — everything found must be an intent to an external app (mail, browser, Play) or Firebase/Billing SDK, never contact data.
   - Analytics: confirm `AnalyticsEvent` payloads contain **no contact fields**
     (names, numbers, emails) — counts and enum labels only.
   - Crashlytics: confirm no PII is attached via custom keys/logs.
   - Exported intents (`ACTION_SENDTO`, `ACTION_VIEW`, chooser): confirm only
     user-initiated and only user-visible data.

4. **Provider & permission handling**
   - All `ContentResolver` calls wrapped (no raw SecurityException paths — the
     revoked-permission crash class was fixed once; check no new unguarded
     `registerContentObserver`/`query`/`applyBatch` appeared).
   - Runtime permission checks precede privileged calls (`READ_PHONE_STATE` paths
     in `data/sim/`).

5. **Dependencies & build**
   - `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -iE "SNAPSHOT|alpha|beta"` — no unstable coordinates in release.
   - R8 on (`isMinifyEnabled`), `debugSymbolLevel` doesn't ship sources,
     debug-only tools (`ui-tooling`, test manifests) are `debugImplementation`.
   - Billing: purchases consumed server-side? (No server here — confirm the
     donation flow grants nothing, so no entitlement to spoof.)

6. **WebView/browser surface** — Custom Tabs only (no WebView); confirm no
   `WebView` import crept in.

Finish with a table: findings by severity, then a short verdict paragraph.
If there are zero high findings, say so explicitly.

Focus (if given): **$ARGUMENTS**

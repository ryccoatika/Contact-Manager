---
name: update-changelog
description: Use whenever you finish a user-facing change (feature, fix, or behavior/UX change) in this repo and before committing it — keep CHANGELOG.md current. The version tag and Play/GitHub release notes are generated from this file by the gradle-changelog-plugin, so a missed entry ships an incomplete release note.
---

# Update the changelog

This repo's release notes are **generated** from `CHANGELOG.md` by the
gradle-changelog-plugin (`./gradlew -q changelogs`). The CI release job feeds
that section into the GitHub Release and the tag. So every user-facing change
must land in `CHANGELOG.md` in the same commit — don't leave it for later.

## When to update

Update it for anything a **user would notice**: new screens/features, changed
behavior, UX tweaks, bug fixes, visible copy changes.

Skip it for invisible-to-users work: CI/workflow edits, build config, refactors
with no behavior change, tests, docs. When unsure, add a short entry.

## How

1. Read the current version from `gradle.properties` — the `appVersionName` line
   (e.g. `1.1`). That is the section the entry belongs in.
2. Open the repo-root `CHANGELOG.md` (Keep a Changelog format).
3. If a `## [<version>] - <YYYY-MM-DD>` heading for that version already exists,
   add a concise, user-facing bullet under it. Don't duplicate an existing point.
4. If that version has **no** section yet, create one **above** the previous
   version, dated with **today's** date, then add the bullet:
   ```
   ## [<version>] - <YYYY-MM-DD>
   - <what changed, from the user's point of view>
   ```
5. Keep bullets short and outcome-focused ("Smoother open animations for contact
   details"), not implementation detail ("wrapped NavHost in SharedTransitionLayout").

## Verify

Run and confirm the new bullet appears:

```bash
./gradlew -q changelogs
```

Then stage `CHANGELOG.md` alongside the change's other files in the same commit.

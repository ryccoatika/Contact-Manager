# CLAUDE.md

The project guide lives in **AGENTS.md** — read it. It is imported below.

@AGENTS.md

## Claude Code specifics

- **Don't device-verify UI changes by default.** `./gradlew compileDebugKotlin`
  and the unit tests are the standard gate; unit tests never touch the UI, and
  that's fine. Run the app on a real device **only when the user asks**, or when
  they invoke the **`/verify-ui`** command (`.claude/commands/verify-ui.md`),
  which drives the build → install → launch → screenshot loop via the
  `run-on-device` skill.
- **After every edit, `./gradlew compileDebugKotlin`.** Fix compile errors before
  moving on; don't batch a screen's worth of edits blind.
- **Commit trailer** (required by this environment):
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- **Presentation-layer rule**: a restyle must not edit `*ViewModel.kt`, `domain/`,
  `data/`, `di/`, or `AppNav.kt`. If it seems to need to, that's a signal to stop.
- For multi-step features use the Superpowers flow (brainstorming → writing-plans
  → execute) and save spec/plan under `docs/superpowers/`.

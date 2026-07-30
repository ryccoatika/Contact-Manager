# CLAUDE.md

The project guide lives in **AGENTS.md** — read it. It is imported below.

@AGENTS.md

## Claude Code specifics

- **Verify UI changes on a real device**, don't just compile. Invoke the
  `run-on-device` skill for the install → launch → screenshot loop, then Read the
  PNG to actually look at the result. Unit tests never touch the UI.
- **After every edit, `./gradlew compileDebugKotlin`.** Fix compile errors before
  moving on; don't batch a screen's worth of edits blind.
- **Commit trailer** (required by this environment):
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- **Presentation-layer rule**: a restyle must not edit `*ViewModel.kt`, `domain/`,
  `data/`, `di/`, or `AppNav.kt`. If it seems to need to, that's a signal to stop.
- For multi-step features use the Superpowers flow (brainstorming → writing-plans
  → execute) and save spec/plan under `docs/superpowers/`.

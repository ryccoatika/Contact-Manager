# CLAUDE.md

The project guide lives in **AGENTS.md** — read it. It is imported below.
The structure contract lives in **docs/ARCHITECTURE.md**.

@AGENTS.md

## Claude Code specifics

- **Gates after every non-trivial edit**: `./gradlew compileDebugKotlin`; before
  any commit also `:app:testDebugUnitTest` and `spotlessCheck`. Fix failures
  before moving on — never batch a screen's worth of edits blind. Run
  `/architecture-audit` before committing structural changes.
- **Don't device-verify UI changes by default.** Compile + unit tests are the
  gate; run on a device only when the user asks or invokes **`/verify-ui`**.
- **Delegate to the project subagents** (`.claude/agents/`): `ui-designer` for
  visual work, `ux-writer` for copy, `test-writer` for tests, `code-reviewer` /
  `architecture-reviewer` before merging non-trivial branches.
- **Commands**: `/lint`, `/architecture-audit`, `/security-audit`,
  `/performance-audit`, `/verify-ui`.
- **Commit trailer** (required): use the Co-Authored-By trailer for the model
  actually running (as instructed by your environment), e.g.
  `Co-Authored-By: Claude <model> <noreply@anthropic.com>`.
- **Presentation-layer rule**: a restyle must not edit `*ViewModel.kt`,
  `domain/`, `data/`, `di/`, or `AppNav.kt`. If it seems to need to, stop.
- **Secrets guard before every commit**: run
  `git diff --cached --name-only | grep -iE '\.jks|\.json|private|google-services' | grep -vE '\.gpg$|\.claude/|release/app-debug\.jks'`
  and investigate any match before committing. Secret plaintext
  (release keystore, google-services.json, play-account.json) is gitignored
  under `release/` — only `.gpg` blobs and the deliberately-public debug
  keystore are committed.
- For multi-step features use the Superpowers flow (brainstorming →
  writing-plans → execute) and save spec/plan under `docs/superpowers/`.
- User-facing change ⇒ update `CHANGELOG.md` in the same commit
  (`update-changelog` skill).

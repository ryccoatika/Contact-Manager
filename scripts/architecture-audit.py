#!/usr/bin/env python3
"""Architecture audit for ContactManager (see docs/ARCHITECTURE.md).

Mechanically checks the dependency/structure rules of the "fenced monolith"
architecture. Violations must either be fixed or already listed in
scripts/architecture-baseline.txt (pre-existing debt being ratcheted down).

Exit 0  = clean (every violation baselined, no stale baseline entries).
Exit 1  = new violations, or stale baseline entries that must be removed.

The baseline may only ever SHRINK. Fixing a violation requires deleting its
baseline line in the same PR; adding a line to the baseline to get a PR green
defeats the entire mechanism and is never acceptable.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
MAIN = REPO / "app/src/main/java/com/ryccoatika/contactmanager"
PKG = "com.ryccoatika.contactmanager"
BASELINE_FILE = REPO / "scripts/architecture-baseline.txt"

# ui/ packages that are shared infrastructure, not features.
SHARED_UI = {"common", "theme", "analytics", "review", "update", "permission", "adaptive"}
# Files allowed to import multiple feature packages (composition roots).
COMPOSITION_ROOTS = {"ui/AppNav.kt", "ui/adaptive"}
# Non-project types a ViewModel may inject without an interface.
VM_PARAM_ALLOWLIST = {"SavedStateHandle", "CoroutineDispatcher", "CoroutineScope"}
# File budget: aim under SOFT (review prompt); HARD only when a file truly
# cannot be decomposed — beyond it the audit fails.
SOFT_FILE_LINES = 500
HARD_FILE_LINES = 750


def kt_files(root: Path):
    return sorted(root.rglob("*.kt"))


def rel(p: Path) -> str:
    return str(p.relative_to(MAIN))


def imports_of(text: str) -> list[str]:
    return re.findall(rf"^import ({re.escape(PKG)}\.[\w.]+)", text, flags=re.M)


def feature_dirs() -> set[str]:
    return {
        d.name
        for d in (MAIN / "ui").iterdir()
        if d.is_dir() and d.name not in SHARED_UI
    }


def in_composition_root(relpath: str) -> bool:
    return any(relpath == root or relpath.startswith(root + "/") for root in COMPOSITION_ROOTS)


def check() -> list[str]:
    violations: list[str] = []
    files = {p: p.read_text() for p in kt_files(MAIN)}
    features = feature_dirs()

    for path, text in files.items():
        r = rel(path)
        imps = imports_of(text)
        layer = r.split("/")[0]

        # R1: domain/ is pure Kotlin — no android/androidx/data/ui/di imports.
        if layer == "domain":
            for m in re.findall(r"^import (android[x]?\.[\w.]+)", text, flags=re.M):
                violations.append(f"R1-domain-purity|{r}|imports {m}")
            for imp in imps:
                sub = imp.removeprefix(PKG + ".").split(".")[0]
                if sub in {"data", "ui", "di"}:
                    violations.append(f"R1-domain-purity|{r}|imports {imp}")

        # R2: data/ and di/ never import ui.
        if layer in {"data", "di"}:
            for imp in imps:
                if imp.startswith(PKG + ".ui"):
                    violations.append(f"R2-data-sees-ui|{r}|imports {imp}")

        # R3: ui files that are NOT ViewModels don't import data
        #     (composables see domain types only; VMs are the data seam).
        if layer == "ui" and not path.name.endswith("ViewModel.kt"):
            for imp in imps:
                if imp.startswith(PKG + ".data."):
                    violations.append(f"R3-composable-imports-data|{r}|imports {imp}")

        # R4: no cross-feature ui imports outside composition roots.
        if layer == "ui" and not in_composition_root(r):
            own = r.split("/")[1] if "/" in r else None
            for imp in imps:
                m = re.match(rf"{re.escape(PKG)}\.ui\.(\w+)\.", imp)
                if m and m.group(1) in features and m.group(1) != own:
                    violations.append(f"R4-cross-feature|{r}|imports {imp}")

        # R6: file budget. The violation string is deliberately stable while the
        # file is over budget (no line count) so baseline entries don't churn.
        # 500–750 is a warning (soft target); >750 is a hard violation.
        if len(text.splitlines()) > HARD_FILE_LINES:
            violations.append(f"R6-file-budget|{r}|over {HARD_FILE_LINES} lines")

        # R7: color literals only in the design system (+ sanctioned palette).
        if "Color(0x" in text and not r.startswith("ui/theme/") and r != "ui/common/AccountVisuals.kt":
            violations.append(f"R7-color-literal|{r}|Color(0x…) outside ui/theme")

        # R8: hardcoded dispatchers only in di/ (elsewhere: injected qualifiers).
        if layer != "di":
            for line in text.splitlines():
                stripped = line.strip()
                if stripped.startswith(("//", "*", "/*")):
                    continue
                if re.search(r"\bDispatchers\.(IO|Default|Main|Unconfined)\b", stripped):
                    violations.append(f"R8-hardcoded-dispatcher|{r}|{stripped[:60]}")
                    break

        # R9: provider access confined to data/ (di/ may wire the resolver in).
        if layer not in {"data", "di"}:
            if re.search(r"\bContactsContract\b|\bcontentResolver\b|content://icc", text):
                violations.append(f"R9-provider-outside-data|{r}|touches ContactsContract/ContentResolver")

        # R10: one event convention — no SharedFlow<String> in ui (typed events only).
        if layer == "ui" and "SharedFlow<String>" in text:
            violations.append(f"R10-stringly-events|{r}|SharedFlow<String>")

        # R11: the design system imports nothing project-local outside itself
        #      (the app's own R class is fine — fonts/drawables are resources).
        if r.startswith("ui/theme/"):
            for imp in imps:
                if not imp.startswith(PKG + ".ui.theme") and imp != PKG + ".R":
                    violations.append(f"R11-theme-deps|{r}|imports {imp}")

        # R12: single findActivity helper.
        if "tailrec fun Context.findActivity" in text:
            violations.append(f"R12-findActivity|{r}|local copy")

        # R13: OEM local-account-type knowledge single-sourced (domain).
        if "vnd.sec.contact.phone" in text and layer != "domain":
            violations.append(f"R13-oem-table|{r}|OEM account-type string outside domain")

        # R14: no ad-hoc full-table snapshots via observe*().first{,OrNull}(...).
        if re.search(r"observe(Contacts|Accounts)\(\)\s*\.first(OrNull)?\s*[({]", text):
            violations.append(f"R14-observe-first|{r}|observe*().first() snapshot")

    # R5: ViewModel constructor deps must be project interfaces (fakeable seams).
    decls = "\n".join(files.values())
    def is_interface(t: str) -> bool:
        return re.search(rf"\b(?:sealed\s+)?interface {t}\b", decls) is not None
    for path, text in files.items():
        if not path.name.endswith("ViewModel.kt"):
            continue
        m = re.search(r"class \w+ViewModel[\s\S]*?constructor\(([\s\S]*?)\)\s*:", text)
        if not m:
            continue
        # Matches `name: Type` regardless of val/private/annotation prefixes.
        for pm in re.finditer(r"\b\w+:\s*([A-Z]\w+)", m.group(1)):
            t = pm.group(1)
            if t in VM_PARAM_ALLOWLIST or not re.search(rf"\bclass {t}\b", decls):
                continue
            if not is_interface(t):
                violations.append(f"R5-concrete-vm-dep|{rel(path)}|injects concrete {t}")

    # R12 is only a violation when duplicated; a single copy is the canonical one.
    fa = [v for v in violations if v.startswith("R12-")]
    if len(fa) == 1:
        violations.remove(fa[0])

    return sorted(set(violations))


def soft_warnings() -> list[str]:
    warnings = []
    for path in kt_files(MAIN):
        n = len(path.read_text().splitlines())
        if SOFT_FILE_LINES < n <= HARD_FILE_LINES:
            warnings.append(f"{rel(path)}: {n} lines (target ≤{SOFT_FILE_LINES}; hard cap {HARD_FILE_LINES})")
    return warnings


def main() -> int:
    violations = check()
    baseline = set()
    if BASELINE_FILE.exists():
        for raw in BASELINE_FILE.read_text().splitlines():
            entry = raw.strip()
            if not entry or entry.startswith("#"):
                continue
            # Strict format so indentation/aliasing can't smuggle entries past
            # the CI ratchet (which counts the same ^R\d+- pattern).
            if raw != entry or not re.match(r"^R\d+-", entry):
                print(f"MALFORMED BASELINE LINE (must be flush-left 'R<n>-…'): {raw!r}")
                return 2
            baseline.add(entry)

    new = [v for v in violations if v not in baseline]
    stale = sorted(baseline - set(violations))

    if "--print-violations" in sys.argv:
        print("\n".join(violations))
        return 0

    ok = True
    if new:
        ok = False
        print(f"NEW VIOLATIONS ({len(new)}) — fix them (never baseline new debt):")
        for v in new:
            print(f"  {v}")
    if stale:
        ok = False
        print(f"\nSTALE BASELINE ({len(stale)}) — fixed! Delete these lines from {BASELINE_FILE.name}:")
        for v in stale:
            print(f"  {v}")
    for w in soft_warnings():
        print(f"warning (soft file budget): {w}")
    baselined = len(violations) - len(new)
    print(f"\naudit: {len(new)} new, {baselined} baselined (ratchet), {len(stale)} stale")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

# Premium Redesign — "Porcelain & Pine"

**Date:** 2026-07-30
**Status:** Approved (direction), ready for implementation planning
**Scope:** All five screens — Home, Detail, Editor, Accounts, Duplicates
**Direction reference:** Approved mockup — `claude.ai/code/artifact/d074bd37-9ae2-4ff2-9a96-8385a9c77c64`

## Goal

Restyle the whole app from generic Material3 defaults into a calm, confident,
premium visual system. **Presentation layer only** — no ViewModel, domain, data,
or navigation logic changes. Every existing feature (account filtering, SIM
routing, move, merge, duplicates, batch selection, permissions) keeps its exact
behavior; only its look changes.

Non-goals: no new features, no data-model changes, no dependency swaps away from
Compose/Material3, no navigation restructure.

## Design system

A small token layer sits under Material3 and drives every screen. Nothing is
hard-coded per screen.

### Color — signature: pine teal on porcelain

Replaces the current warm Teal/Sage/Terracotta fallback. Both themes are
first-class (dark is designed, not inverted).

| Token | Light | Dark | Role |
|---|---|---|---|
| ground (background/surface) | `#F4F6F3` porcelain | `#0F1512` warm ink | app ground |
| surface (card) | `#FFFFFF` | `#171E1A` | cards, sheets |
| surfaceVariant | `#ECEFEA` | `#212A25` | search pill, chips |
| primary (pine) | `#0E5A51` | `#74D6C6` | signature action, selected state |
| onPrimary | `#FFFFFF` | `#06352E` | |
| primaryContainer (soft) | `#D7EDE7` | `#213330` | quick-action pills, type pills |
| onPrimaryContainer | `#063B34` | `#BFEDE4` | |
| ink (onSurface) | `#14201B` | `#E8EEEA` | primary text |
| muted (onSurfaceVariant) | `#5D6B64` | `#94A39C` | secondary text |
| outline / hairline | `rgba(20,32,27,.09)` | `rgba(230,240,235,.10)` | borders |

**Extended tokens** (not in Material3 `ColorScheme`, provided via a
`CompositionLocal`):

- `brass` — `#8F6423` light / `#D9AE6E` dark, plus `brassContainer`
  (`#F1E7D4` / `#2C2417`). Used **sparingly**: SIM tags, the duplicate "why it
  matched" reason. This is the premium metal note, never a second primary.
- `avatarGradients` — six desaturated jewel gradients (teal, indigo, plum,
  brass, rose, slate). Deterministically chosen per contact by name hash.

Dynamic color (Monet) stays available as an **opt-in** on Android 12+, but the
default flips to this branded palette (`dynamicColor = false` by default) so the
app has a consistent identity out of the box.

**Account provenance colors** (`AccountVisuals.color`) get retuned from the
current garish set (`#4285F4`, `#DB4437`, …) to a muted premium palette so the
dots read as quiet metadata, not decoration. Labels/logic unchanged.

### Type

Full Material3 type scale (currently only `bodyLarge` is set). Tuned for a
premium read: tighter tracking on large display/headline, comfortable body line
height, `FontWeight.SemiBold`/`Bold` for names and titles. Phone numbers and
counts use `TextStyle(fontFeatureSettings = "tnum")` (tabular numerals) so
digits align.

Typeface: **Manrope** (OFL), loaded via Compose **Downloadable Google Fonts**
(`androidx.compose.ui:ui-text-google-fonts`) — no binary bundled, resolved at
runtime through the Google Fonts provider with a graceful fallback to
`FontFamily.Default` (Roboto) when the provider is unavailable. Manrope drives
every role (display through label); the tuned scale carries the fallback. Early,
isolated task.

### Shape

New `Shapes`: small `12.dp`, medium `20.dp`, large `28.dp`. Cards `20.dp`,
search/chips fully rounded, FAB a `20.dp` squircle, avatars circular.

### Reusable components (`ui/common`)

Extract what is currently copy-pasted across screens into shared composables:

- `ContactAvatar(name, photoUri, size)` — gradient initials fallback, photo when
  present. Replaces the three near-identical avatar blocks in Home/Detail/Duplicates.
- `SelectedAvatar` — check-mark selection state (Home batch mode).
- `AccountDot(type, name, size)` — the provenance dot.
- `CapabilityTag(capability)` — Full access / Limited / Read-only / SIM pill.
- `SectionCard { }` — the grouped surface+hairline card container.
- `QuickActionPill(icon, label, onClick)` — soft pine pill (Detail actions).
- `SearchField(query, count, onQueryChange)` — the porcelain search pill.

## Per-screen redesign

Behavior identical to today; only composition/styling changes.

### Home
Large `Contacts` title; `SearchField` pill; account filter `FilterChip`s show a
provenance dot + name + count (selected = filled pine); gradient `ContactAvatar`
rows at min 64.dp with a subtitle (org/first number) and trailing provenance
dots; sticky section letters in pine; refined alphabet rail; **squircle FAB**.
Batch selection top bar / bottom bar restyled to match; move/merge/delete sheets
and dialogs reuse `SectionCard`/`AccountDot`.

### Detail
Tonal **hero** card: a soft pine radial tint behind a 86.dp `ContactAvatar`,
name (headline), org subtitle, then a row of `QuickActionPill`s
(Call / Message / Video / Edit). Each raw contact becomes a titled `SectionCard`
(account dot + label, `CapabilityTag` for SIM), grouped fields with tabular
numerals, hairline dividers, and Edit/Delete as bordered buttons. Read-only
entries keep the lock + "managed by …" note.

### Editor
Fields grouped into `SectionCard`s: name block; a "Phone" group with an inline
type pill and "Add phone"; an "Email" group; organization/note. `Save to`
account destination is an explicit row with an `AccountDot` (not a bare
dropdown). SIM mode keeps its name/number-only form and length counter, restyled.

### Accounts
Each account is a card row: a colored rounded **icon tile** (provider glyph),
name + one-line description, a bold count, and a capability caption
(Full access / Limited / Read-only). Overflow "Move all contacts to…" and the
move sheet/dialog keep behavior, restyled. Pull-to-refresh and the phone-permission
prompt stay.

### Duplicates
Each group is a `SectionCard`: `ContactAvatar` + name, the **match reason in
brass** ("Same number · …"), member mini-rows (dot + name + source), a primary
pine **Merge** button, with Link / Not-duplicate as quieter actions. Confidence
(Likely/Possible) shown as a tag. Empty state restyled. Merge picker sheet and
confirm dialog reuse shared components.

## Files touched

- **Rewrite:** `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`; add
  `ui/theme/Shape.kt` and an extended-color `CompositionLocal`.
- **Retune palette:** `ui/common/AccountVisuals.kt` (colors only; labels/logic kept).
- **New shared composables** in `ui/common/`.
- **Restyle (composition only):** `HomeScreen.kt`, `DetailScreen.kt`,
  `EditorScreen.kt`, `AccountsScreen.kt`, `DuplicatesScreen.kt`.
- **Optional:** `res/font/` + font-family wiring.

Untouched: all `*ViewModel.kt`, `domain/`, `data/`, `di/`, navigation (`AppNav.kt`).

## Verification

- ViewModel/domain unit tests must still pass unchanged (proves logic untouched).
- Add `@Preview` (light + dark) for each screen's key composables so the design
  is verifiable without a device and future changes are visible.
- Build the app and eyeball each screen in light and dark against the approved
  mockup.
- Manual smoke: search, filter by account, batch select → move/merge/delete,
  open detail, edit/create (incl. SIM), accounts move-all, duplicates merge —
  confirm all still work.

## Risks

- **Dynamic-color default flip** changes the look on Android 12+ devices that got
  Monet before. Intended (brand identity), reversible via the `dynamicColor` flag.
- **Contrast:** every text/background pair must stay legible in both themes —
  check on the porcelain ground and the near-black ground.
- Bundled font adds a small APK cost and a licensing note (OFL) — hence optional.

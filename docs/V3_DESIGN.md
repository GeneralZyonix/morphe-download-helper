# Morphe design — v3 direction

Status: **v3.0.4 stable on `main`.** Morphe is the only home design; Classic has been removed and migrated.

## Goal

Make Morphe the only home design and feel like one product with Morphe Manager.
Dark / AMOLED / Light / System, all accent presets, custom schemes, and Material
You stay available.

## Visual language

- Deep navy-to-black surfaces with soft violet and blue glows.
- Large 28 dp cards, generous spacing, and a single bold focal card per screen.
- Gradient hero cards with an overline, one large statement, short body copy,
  and one primary pill action. The pill uses a light accent gradient with dark
  accent text, so it follows the selected accent instead of staying white.
- Paired feature tiles with a coloured icon tile, title, one-line description,
  and a circular arrow.
- Rounded pill bottom navigation with four destinations: **Home**,
  **Downloads**, **Sources**, **Settings**. Tab changes slide horizontally:
  the outgoing page moves aside while the incoming page follows the finger
  direction, over 260 ms.
- Typography: bold display for the hero, medium titles, 11–13 sp body text,
  uppercase overline with wide tracking.
- Motion: short spring press, tab cross-fade, and a soft selected-pill morph.
  Respect the system animation scale.

## Information architecture

| Tab | Purpose |
| --- | --- |
| **Home** | Status chips, hero, Visual guide, Helper log, Help, developer links. |
| **Downloads** | Local APK library, history, and delete-all. |
| **Sources** | The source list from Settings, embedded with toggles and order controls. |
| **Settings** | In-shell section list; each section opens a sheet over the shell. There is no redundant Done button. |

The old main screen mixes all of this into one long scroll. The shell keeps the
main actions visible and gives downloads and sources a stable home.

## Phases

1. **Shell prototype** — four-tab pill navigation, gradient Home, live source
   toggles, local download list, and settings shortcuts. **Done.**
2. **Promote the shell** — make Morphe the launcher entry point, move startup
   work into it, and delete the Classic home. **Done.**
3. **Sources** — the embedded panel has live toggles and ordering. **First pass
   done**; the full manager still adds Test all and GitHub apps.
4. **Downloads** — persisted history of successful hand-offs with app name,
   package, version, source, size, and timestamp. Entries survive file cleanup
   and show as removed; current files still open and delete. **Done** for the
   first pass.
5. **Settings** — Morphe header (overline, display title, subtitle) and gradient
   section icon tiles; the existing sheets inherit the larger type. **First pass
   done.**
6. **Download and update screens** — gradient backgrounds, Morphe display
   headers, gradient update badge, and a dedicated failure hero with retry,
   switch-source, diagnostics, and close actions. **Done.**
7. **Polish** — motion, haptics, large-font and RTL passes, screenshots, and a
   size comparison.

## Implemented in this branch

- `MorphePreviewActivity` is the launcher entry point and owns notification
  permission, cleanup, launcher-icon state, theme changes, and update checks.
- The legacy `design_style` preference is removed on first launch; Appearance
  no longer has a design switch.
- The old `MainActivity` home and its duplicate log/report code are deleted.
- `DownloadHistory` stores up to 40 successful hand-offs and is written from
  `DownloadActivity` after validation and VirusTotal checks.
- The Downloads tab merges history with the current APK list, resolves public
  files through MediaStore, and distinguishes kept versus removed files.

## Screenshots

- `docs/branding/material3/v3-shell-home.png`
- `docs/branding/material3/v3-shell-downloads.png`
- `docs/branding/material3/v3-shell-sources.png`
- `docs/branding/material3/v3-shell-settings.png`
- `docs/branding/material3/v3-shell-log-sheet.png`
- `docs/branding/material3/v3-shell-storage-sheet.png`
- `docs/branding/material3/v3-guide.png`
- `docs/branding/material3/v3-update.png`
- `docs/branding/material3/v3-download.png`
- `docs/branding/material3/v3-download-failure.png`

## Guardrails

- Classic is gone; existing `design_style` values are removed during migration.
- AMOLED must stay true black. Material You must keep working on Android 12+.
- Do not reduce contrast on warnings or VirusTotal decision buttons.
- Keep `minSdk 23`; no Compose-only dependency is required for this direction.
- The APK still uses the debug key for sideloading. No signing change here.

## Current scope

The branch now implements the first pass of phases 1–5:

- `MorphePreviewActivity` with the four-tab shell.
- Home: header, status chips, gradient hero, guide/log tiles, Help, developer
  links, and the build variant.
- Downloads: persisted history plus current APKs, with delete-all in the tab
  header instead of on Home.
- Theme and accent changes recreate the shell and preserve the selected tab.
- Helper log: one sheet with Share, Copy, Save, Preview, Report, Suggest, and
  Clear. Every export is sanitized first; Save uses the system file picker.
  The sheet handle drags down to dismiss, with the same snap-back behaviour.
- Sources lives only in the bottom navigation, not as a duplicate home button.
- Pages add a real bottom spacer so the floating navigation bar never covers
  the last row and vertical scrolling starts from cards and buttons too.
- Downloads: persisted hand-off history plus current APKs from the cache and
  public `Downloads/Morphe Helper` folder.
- Sources: live enabled state and order from `SourceSettings`.
- Settings: the tab stays inside the shell and uses the same Morphe header,
  gradient tiles, and separate section cards as the full screen. Each row opens
  its section as a transparent sheet host over the shell; the handle drags down
  to dismiss, and a short drag springs back.
- Appearance: theme, accent, and colour controls only; no design switch.
- Guide, Update, Download, and Appearance use the Morphe gradient background
  and display headers. Update also gets the gradient update tile.
- The Download failure state has its own error hero: red gradient card, warning
  tile, plain-language reason, Retry all / Retry failed, Switch source, Copy
  details, Edit sources, Open download page (when available), and Close. The
  source-attempt diagnostics sit in a separate "What happened" card. Download
  logic, WebView flows, and VirusTotal states are unchanged.

The shell is the launcher home. The old classic home screen is deleted, and the
legacy design preference is removed on upgrade.

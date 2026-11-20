# Morphe design — v3 direction

Status: **prototype branch `v3-design-poc`.** Not merged, not released.
This is the design plan behind the optional Morphe design preview. The stable
v2.7.3 release continues to use the current Material 3 Expressive screens.

## Goal

Add an **optional Morphe design** for a future major release. It should feel
like one product with Morphe Manager, not like another accent colour. Existing
Dark / AMOLED / Light / System, all accent presets, custom schemes, and Material
You stay available. The current design remains selectable.

## Visual language

- Deep navy-to-black surfaces with soft violet and blue glows.
- Large 28 dp cards, generous spacing, and a single bold focal card per screen.
- Gradient hero cards with an overline, one large statement, short body copy,
  and one primary pill action.
- Paired feature tiles with a coloured icon tile, title, one-line description,
  and a circular arrow.
- Rounded pill bottom navigation with four destinations: **Home**,
  **Downloads**, **Sources**, **Settings**.
- Typography: bold display for the hero, medium titles, 11–13 sp body text,
  uppercase overline with wide tracking.
- Motion: short spring press, tab cross-fade, and a soft selected-pill morph.
  Respect the system animation scale.

## Information architecture

| Tab | Purpose |
| --- | --- |
| **Home** | Status chips, hero, Visual guide, Helper log, developer links. |
| **Downloads** | Local APK library: name, size, date, open/share/delete. Persistent history is a later data-layer change. |
| **Sources** | The source list from Settings, embedded with toggles and order controls. |
| **Settings** | Section shortcuts into the existing Settings screen. |

The old main screen mixes all of this into one long scroll. The shell keeps the
main actions visible and gives downloads and sources a stable home.

## Phases

1. **Shell prototype** — preview activity with the four-tab pill navigation,
   gradient Home, live source toggles, local download list, and settings
   shortcuts. Entry from Appearance, labelled as a preview. **Done.**
2. **Promote the shell** — add an opt-in **Design** setting. Classic remains the
   default; Morphe makes the launcher open the four-tab shell. Switching back to
   Classic returns to the existing home screen. **Done.**
3. **Sources** — the embedded panel has live toggles and ordering. **First pass
   done**; the full manager still adds Test all and GitHub apps.
4. **Downloads** — persisted history of successful hand-offs with app name,
   package, version, source, size, and timestamp. Entries survive file cleanup
   and show as removed; current files still open and delete. **Done** for the
   first pass.
5. **Settings** — Morphe header (overline, display title, subtitle) and gradient
   section icon tiles; the existing sheets inherit the larger type. **First pass
   done.**
6. **Download and update screens** — last, because the progress ring, WebView
   verification, and VirusTotal states are the highest-risk surfaces.
7. **Polish** — motion, haptics, large-font and RTL passes, screenshots, and a
   size comparison.

## Implemented in this branch

- `ThemeManager.DESIGN_CLASSIC` / `DESIGN_MORPHE` stored as `design_style`.
- Appearance → **Design** selector with Classic and Morphe options, plus a
  direct **Open Morphe design** action while Morphe is selected.
- `MainActivity` redirects to the shell only when Morphe is selected;
  `MorphePreviewActivity` returns to the classic screen when Classic is selected.
- `DownloadHistory` stores up to 40 successful hand-offs and is written from
  `DownloadActivity` after validation and VirusTotal checks.
- The Downloads tab merges history with the current APK list, resolves public
  files through MediaStore, and distinguishes kept versus removed files.

## Screenshots

- `docs/branding/material3/v3-shell-home.png`
- `docs/branding/material3/v3-shell-sources.png`
- `docs/branding/material3/v3-shell-settings.png`

## Guardrails

- The Morphe design is opt-in. Never force an existing user onto it.
- AMOLED must stay true black. Material You must keep working on Android 12+.
- Do not reduce contrast on warnings or VirusTotal decision buttons.
- Keep `minSdk 23`; no Compose-only dependency is required for this direction.
- The APK still uses the debug key for sideloading. No signing change here.

## Current scope

The branch now implements the first pass of phases 1–5:

- `MorphePreviewActivity` with the four-tab shell.
- Home: header, status chips, gradient hero, guide/log tiles, developer links.
- Downloads: persisted hand-off history plus current APKs from the cache and
  public `Downloads/Morphe Helper` folder.
- Sources: live enabled state and order from `SourceSettings`.
- Settings: shortcuts to each existing Settings section, with a Morphe header
  and gradient section tiles when the design is selected.
- Appearance: **Design** selector (Classic / Morphe) and a direct shell action.

The shell remains a separate activity rather than a rewrite of MainActivity, so
the design can still be turned off and the classic screen restored at any time.

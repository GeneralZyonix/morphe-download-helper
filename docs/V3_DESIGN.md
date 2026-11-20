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
   shortcuts. Entry from Appearance, labelled as a preview.
2. **Promote the shell** — make MainActivity host the same tabs when the Morphe
   design is selected. Keep the classic screen for everyone else.
3. **Sources** — extract the full source manager (test all, GitHub apps,
   reordering) into the embedded panel.
4. **Downloads** — add a persisted download history with package, version,
   source, status, and actions. Today the tab can only list files that still
   exist.
5. **Settings** — restyle the section list and sheets with the Morphe spacing
   and navigation, without changing the settings themselves.
6. **Download and update screens** — last, because the progress ring, WebView
   verification, and VirusTotal states are the highest-risk surfaces.
7. **Polish** — motion, haptics, large-font and RTL passes, screenshots, and a
   size comparison.

## Guardrails

- The Morphe design is opt-in. Never force an existing user onto it.
- AMOLED must stay true black. Material You must keep working on Android 12+.
- Do not reduce contrast on warnings or VirusTotal decision buttons.
- Keep `minSdk 23`; no Compose-only dependency is required for this direction.
- The APK still uses the debug key for sideloading. No signing change here.

## First-slice scope

The prototype in this branch implements phase 1:

- `MorphePreviewActivity` with the four-tab shell.
- Home: header, status chips, gradient hero, guide/log tiles, developer links.
- Downloads: scans `AppSettings.downloadDir()` and lists APK files.
- Sources: live enabled state and order from `SourceSettings`.
- Settings: shortcuts to each existing Settings section.
- Appearance: **Preview Morphe design** row.

It is deliberately an activity, not a rewrite of MainActivity. That keeps the
prototype installable and reversible while the direction is reviewed.

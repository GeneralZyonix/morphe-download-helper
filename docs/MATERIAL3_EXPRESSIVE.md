# Material 3 Expressive test branch

Status: **v2.5.0 pre-release, built from `material3-poc`.** `main` remains on
the stable v2.3.10 release.
Branch: `material3-poc`  
Base: `main` at v2.3.10 (`a05fea2`)

This branch keeps the existing theme system and rebuilds the visual language around
Material 3 Expressive tokens, shapes, typography, and motion. It is a Views
modernisation, not a Compose migration.

## What changed

### 1. Design tokens

`M3Ui` maps the existing `ThemePalette` to Material 3 colour roles:

| M3 role | Source |
| --- | --- |
| `surface` | `palette.bgTop` |
| `surfaceContainer` | `palette.cardTop` |
| `surfaceContainerHigh` | card blend |
| `outlineVariant` | border softened into the card |
| `primary` / `onPrimary` | saved accent gradient start / readable foreground |
| `primaryContainer` | accent mixed into the card surface |
| `secondaryContainer` | quieter accent tint |
| `error` / `errorContainer` | existing red and warning palette |

This means Dark, AMOLED, Light, System, every accent preset, custom schemes,
surface tinting, and Material You all keep working without a second colour
source.

### 2. Shapes and type

- Cards and sheets: **28 dp** radius.
- Fields and inner rows: **16–20 dp** radius.
- Buttons and chips: **fully rounded pills**.
- Display headings: 27–30 sp, bold, tight tracking.
- Titles: `sans-serif-medium` with slight negative tracking.
- Overlines: 11 sp, bold, 0.12 letter spacing, uppercase.
- Cards are flat with a 1 dp `outlineVariant` border instead of a vertical
  gradient. The brand gradient survives on the primary call to action and the
  app logo.

### 3. Expressive motion

`M3Ui.motion(...)` adds a small press response: controls contract to 97% and
spring back with a slight overshoot. Ripples and normal click handling remain
unchanged.

### 4. Screen coverage

| Screen | What changed |
| --- | --- |
| `MainActivity` | tonal hero, 28 dp cards, pill actions, tonal/outlined hierarchy, chips, log actions |
| `SettingsActivity` | 28 dp section cards, tonal icon tiles, 20 dp rows, tonal button, M3 chip for “Expand all” |
| `AppearanceActivity` | display header, tonal preview, solid-accent mode selector, M3 accent tiles, M3 fields/buttons |
| `GuideActivity` | numbered step cards, framed and clipped screenshots, display header, pill CTA |
| `UpdateActivity` | display title, 28 dp cards, pill actions, tonal version picker |
| `DownloadActivity` | 28 dp progress/diagnostics cards, tonal chips, pill controls, warning/error containers |
| `SourceManagerDialog` | bottom sheet with handle, rounded top corners, tonal test button, filled Done button |
| `ThemeManager` | 28 dp dialogs, medium titles, sentence-case buttons |

## Material 3 preview activity

`M3PocActivity` is a separate design laboratory, built only into **debug** APKs.
It uses the real Material Components 1.14.0 widgets (MaterialSwitch, Chip,
LinearProgressIndicator, BottomSheetDialog, MaterialCardView) under an actual
`Theme.Material3Expressive` theme, and demonstrates:

- **Standard**: dynamic colour on Android 12+.
- **Brand layer**: Aurora-toned surfaces and a gradient hero.

Material Components is declared as `debugImplementation`, so it never ships in
the release APK. The POC lives in `app/src/debug/` with its own manifest,
strings, and styles.

It is exported for ADB launching on debug builds only:

```powershell
adb shell am start -n be.alterra.morphehelper/.M3PocActivity
adb shell am start -n be.alterra.morphehelper/.M3PocActivity --es variant brand
```

Remove the `src/debug` preview before any merge or release.

## Verification

Automated:

- Unit tests: **42/42** on `iconDebug`, **42/42** on `noiconDebug`.
- Instrumentation: **44/45** on `iconDebug` (API 36 emulator). Excluding the
  class below, the final build passes **44/44**.
- The single failure, `ApkCubeNotFoundTest.transientNoMatchNavigatesImmediately`,
  also fails on untouched `main` in a clean worktree. It is a timing-sensitive
  WebView test and is not caused by this branch.

Manually checked on the API 36 emulator:

- Dark, Light, and AMOLED modes.
- Main, Settings, Appearance, Guide, Update changelog, Download progress,
  Download failure, Sources bottom sheet, and the sanitized log preview.
- Accent switching from Aurora to another preset and back through System.
- Existing 2.3.10 log sanitisation still works.

Not yet checked:

- Physical phone, rotation, large display font, TalkBack.
- Every accent preset and every custom scheme on a real device.

## Manual test checklist

### Theme

- [ ] Dark, AMOLED, Light, System each apply immediately and survive recreation.
- [ ] At least Aurora, Mint, Gold, Mono, and Material You (Android 12+).
- [ ] Custom scheme with a valid HEX, and the editor’s error state with an empty value.
- [ ] “Tint backgrounds from accent” on/off.
- [ ] Status and navigation bar icons stay readable in Light and Dark.

### Main

- [ ] Open Morphe, Visual guide, Help.
- [ ] Sources opens Settings scrolled to the Sources section.
- [ ] Delete all downloads confirmation.
- [ ] Helper log expands; Share, Copy, Save, Clear work.
- [ ] Preview sanitized log shows redacted text; Close/Copy log work.
- [ ] GitHub report and feature request links.

### Settings

- [ ] Every section expands/collapses; Expand all toggles.
- [ ] Source rows reorder and toggle; at least one source stays enabled.
- [ ] Test all sources and individual source tests.
- [ ] Language, Appearance, icon state, updates, backup, storage, security,
  battery, and Shizuku rows still open their dialogs.
- [ ] Dialogs are readable in Light and Dark.

### Appearance and Guide

- [ ] Mode selector has an obvious selected state in all modes.
- [ ] Accent tiles show the swatch, selected border, and check.
- [ ] Guide step cards and screenshots are readable; screenshots are clipped
  into rounded frames.

### Download

- [ ] Normal progress screen, source steps, pause/resume, cancel.
- [ ] Failure screen shows diagnostics and working Retry/Copy/Edit sources.
- [ ] Sources bottom sheet opens from the failure screen, scrolls, reorders,
  toggles, and closes.
- [ ] Update available/changelog flow still opens `UpdateActivity`.

## Known limitations

- The app is still Views-based. Material Components is in maintenance mode, so
  this is a one-time visual modernisation rather than a long-term Compose plan.
- Material Components is a **debug-only** dependency for the POC; the production
  screens use the token layer because a Material theme would override the app’s
  manual Dark/AMOLED/Light/System modes. The release APK is 3.54 MB; the debug
  APK with the POC is 9.4 MB.
- The launch splash is still the dark brand splash in every mode.
- Switches remain framework `Switch` widgets with M3 tints; a custom M3 switch
  was tried and reverted because the platform thumb/track sizing clipped.
- Some framework `ProgressBar` and `AlertDialog` internals remain.

## Build

```powershell
.\gradlew.bat :app:assembleIconDebug :app:assembleNoiconDebug
.\gradlew.bat :app:testIconDebugUnitTest :app:testNoiconDebugUnitTest
```

APKs:

- `app/build/outputs/apk/icon/debug/app-icon-debug.apk`
- `app/build/outputs/apk/noicon/debug/app-noicon-debug.apk`

## Screenshots

See `docs/branding/material3/`:

- `main-dark.png`, `main-light.png`, `main-amoled.png`
- `settings-dark.png`
- `appearance-dark.png`, `appearance-light.png`
- `guide-dark.png`, `guide-dark-bottom.png`
- `download-dark.png`, `download-failure-light.png`
- `update-dark.png`
- `sources-sheet.png`
- `sources-sheet-main-2.4.2.png`
- `settings-sections-2.5.0.png`, `settings-sheet-sources-2.5.0.png`,
  `settings-sheet-general-2.5.0.png`
- `log-actions-2.4.1.png`, `sources-compact-2.4.1.png`
- `poc-standard.png`, `poc-brand.png`, `poc-sheet.png`

## Rollback

Nothing on this branch is merged. To return to the release line:

```powershell
git switch main
```

Delete the branch only after the design review.

# Changelog

All notable changes to Morphe Download Helper are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a simple `MAJOR.MINOR` version scheme.

## [2.0.1-beta.5] - 2026-11-12

Fifth 2.0.1 beta: complete French translations for the remaining download-screen
strings.

### Fixed

- French translations for the remaining download progress captions, the
  verification attention hint, and the short **Switch source** label.

## [2.0.1-beta.4] - 2026-11-12

Fourth 2.0.1 beta: download-screen polish and a settings persistence fix.

### Changed

- Download screen polish: the active source logo now appears in the app card,
  chips and package pill are highlighted, metric icons were added, the active
  source is green in the collapsed summary, source rows can be tapped to
  switch, the chevron has a proper circular style, source labels are shorter,
  and the status line shows speed/ETA without a duplicate percentage.
- The failure screen now uses the same app card layout as the download screen.
- Missing app icons use an app-initial placeholder instead of the generic
  Android icon.

### Fixed

- Settings changes to retention, download folder/custom folder, and the
  VirusTotal API key now persist instead of being overwritten by the public
  `helper-settings.txt` file when the screen reloads.

## [2.0.1-beta.3] - 2026-11-11

Third 2.0.1 beta: the download source card collapses during downloads, and
source management moves to a single **Sources** button.

### Changed

- The download source card collapses to a compact summary while a download is
  active. It expands automatically on a source failure, a fallback note, or
  **Switch source**, and can be toggled by tapping its header.
- The main screen no longer lists sources. A **Sources** button opens the
  Sources section in Settings directly.

## [2.0.1-beta.2] - 2026-11-11

Second 2.0.1 beta: download-screen redesign, automatic resume, WebView
fail-fast, and source fixes.

### Added

- Download screen redesign: target app icon and name, version/format/ABI
  chips, Source/Size/Connections metrics, a package pill, source logos with an
  active **Now** pill, and distinct Pause/Switch/Cancel action styling.
- Single-stream downloads now retry a dropped connection up to three times and
  resume from the `.part` offset with `Range` and `If-Range`. Multi-connection
  segments also resume from their written offset instead of starting over.

### Fixed

- GitHub downloads no longer reject an official APK because the installed app
  has a different signature. Patched apps are normally signed with a different
  key; package and version validation still run, and Morphe handles the patch
  signature step.
- APKMirror WebView navigation now prefers the requested version (or the app
  page that lists it) instead of silently downloading the newest release. A
  rejected automatic APKMirror build also no longer blocks an exact
  resolver-supplied APKMirror link, and **Retry all sources** clears the
  per-request source rejection flags.
- Morphe receives the APK before the helper copies it to
  `Downloads/Morphe Helper` or a custom folder. The copy runs in the background
  while the foreground service stays alive, and the log records hand-off and
  copy timings.
- Metadata requests use shorter fail-fast timeouts, and file downloads use a
  tighter stall timeout. APKMirror pages keep a little more room for Cloudflare.
- WebView flows log their state transitions and fail fast on main-frame load
  errors, renderer crashes, and permanent HTTP errors instead of waiting for
  the 90-second watchdog.

## [2.0.1-beta.1] - 2026-11-08

First 2.0.1 beta with per-app GitHub sources and download-flow fixes.

### Added

- **Per-app GitHub sources**: map a package name to a GitHub repository, with an
  optional asset-name filter and a **Try this repo first** switch. Preferred
  mappings run before the Morphe link; fallback mappings stay available in
  **Switch source**. User-added entries are marked **USER** and exported
  configurations import them back as user-added.
- Download screen: stage captions inside the progress ring, larger app details,
  and **Cancel / Switch source** controls directly under the ring. The source
  picker pauses an active download and resumes it when dismissed.

### Fixed

- **Switch source** now aborts the current attempt and immediately retries with
  the selected source, including during resolution and WebView handoffs.
- General source descriptions in Settings use the neutral muted colour again;
  warning colours are reserved for real warnings (issue #26). The
  APKPure/Uptodown WebView note now sits below the source list as normal
  information instead of appearing as a yellow warning at the top.
- The download failure screen's **Edit sources** button now opens the same
  **Download sources** popup as the main screen instead of switching to
  Settings, and the failure screen offers **Retry all sources** to restart the
  complete ordered search.

## [2.0] - 2026-11-08

First stable release of the 2.0 redesign. Further fixes will be handled in a
2.0.1 beta if needed.

### Added

- **Patch Pass launcher icon**, with adaptive foreground/background/monochrome
  layers and a fixed cyan → purple → magenta palette.
- **Theme-aware in-app logo**: the Main and Settings headers, the Appearance
  preview, and each accent option render Patch Pass in the selected scheme.
- Theme overhaul: AMOLED, light, dark, and system modes; accent presets, custom
  scheme HEX, custom accent HEX, tinted surfaces, and consistent dialog/theming.
- Focused source manager on Main, arbitrary **Stop & switch source** picker,
  multi-connection downloads, detailed download information, and the
  Morphe-style gradient progress ring.
- VirusTotal three-way detection action (**Ask / Always block / Always
  continue**), flagged engine names, report link, and explicit
  **Continue anyway / Delete and cancel** decisions.
- Shizuku fork support via a configurable manager package, with live-binder
  status detection before package checks.
- Update reliability: HTTP/1.1 GitHub traffic, retries, release-feed fallback,
  raw-changelog fallback, and **Open release page**.
- Uptodown WebView improvements: HTTP/1.1 CDN downloads, automatic cookie
  banner rejection, no automatic scroll away from the download screen, and a
  verification hint when the page is still waiting.
- XAPK/APKS validation hardening and fixture tests, including manifest/APK
  cross-checks and package checks for unversioned requests.

### Fixed

- Exact `versionCode` matching when Morphe supplies one, including APKMirror
  and split-archive paths.
- Retry failed sources re-includes the parent Morphe link after a failed
  Uptodown/APKMirror/APKPure attempt.
- Uptodown now keeps **Morphe link** marked as the active source instead of
  advancing the list to APKMirror while the WebView is still working.
- Automatic update checks and downloads no longer depend on a healthy
  `api.github.com` or `github.com` connection.

## [2.0.0-beta.8] - 2026-11-08

Update check recovery when GitHub's API and website are both unavailable.

### Fixed

- Update checks now read the `CHANGELOG.md` served by
  `raw.githubusercontent.com` as their first source. That host is separate from
  both `api.github.com` and `github.com`.
- Beta.7 could still fail on networks where the GitHub API returned 503/504 and
  `github.com` itself could not be resolved or served the Atom feed. Update
  checks now succeed through the raw changelog in that situation.
- Release notes and the APK URL are reconstructed from the changelog when the
  GitHub API is unavailable.
- The HTTP/1.1, retry, release-feed, **Open release page**, and Shizuku fork
  fixes from beta.6/beta.7 remain in place.

## [2.0.0-beta.7] - 2026-11-08

Update network follow-up for GitHub HTTP/2 failures.

### Fixed

- Update checks and downloads now use HTTP/1.1 for GitHub. Mobile networks that
  reset HTTP/2 streams with `REFUSED_STREAM` no longer break **Check for
  updates**.
- Update checks retry up to three times with a short delay before reporting a
  failure, matching the download retry behaviour.
- If `api.github.com` is unavailable, update checks fall back to GitHub's release
  Atom feed, so a working GitHub website is enough to find new versions.
- Update check attempts are now written to the helper log so future failures are
  easier to diagnose.

## [2.0.0-beta.6] - 2026-11-08

Update reliability follow-up.

### Added

- The Shizuku manager package is now configurable under Settings → Updates for
  forks that use a different package name (for example Shizuku+). Forks that
  keep the original package, such as Shizuku Next, need no change. A live binder
  is trusted first, so a running fork with permission granted is recognised.

### Fixed

- Shizuku status now trusts a live Shizuku binder before checking the manager
  package name, so a renamed fork is recognised even though its package differs.
- Update downloads now retry GitHub asset requests up to three times. A 504 from
  GitHub's release CDN no longer leaves the update stuck at "Installing with
  Shizuku".
- The "found update" message now says it is downloading the APK for Shizuku,
  rather than claiming installation has already started.
- Failed update downloads now offer **Open release page** so the APK can be
  downloaded manually.
- The Shizuku installer now passes `--user 0`, merges stderr into the result,
  and logs the `pm install` output for diagnosis.

## [2.0.0-beta.5] - 2026-11-08

Test beta completing the remaining follow-up work from beta.4.

### Added

- **Focused source manager on Main.** Manage now opens a dialog over the Main
  screen with toggles, reordering arrows, and Test all sources. It no longer
  opens the full Settings screen.
- **Stop & switch source** now opens a source picker. Pick any enabled source
  and the current attempt is cancelled and restarted from that source.
- **VirusTotal detections** now has three modes: **Ask me**, **Always block**,
  and **Always continue**. The default is Ask.
- Detection results now include the names of flagged VirusTotal engines, with
  the report link still available.
- The progress ring now uses a gradient sweep and a rotating comet-style
  indeterminate arc, closer to the Morphe updater.

### Changed

- Existing `virustotal_block_detections` settings migrate to the new three-way
  setting.
- Settings backup includes `virustotal_detection_action` while keeping the old
  boolean for compatibility.

## [2.0.0-beta.4] - 2026-11-08

Follow-up beta focused on safer downloads, source recovery, and theme polish.

### Added

- **Continue anyway / Delete and cancel** choices after a VirusTotal detection,
  quota abort, or pending analysis. The fixed five-second wait is gone for clean
  and unscanned results.
- A **View on VirusTotal** link and a safer cancel confirmation when a validated
  file already exists. Back during the result stage no longer deletes the file.
- **Delete all downloaded APKs** on Main, with a confirmation dialog. Settings
  backups are preserved.
- **Expand all / Collapse all** in Settings and a focused Sources view when
  opening **Manage** from Main.
- **Stop & switch source** while downloading. It aborts the current attempt and
  moves to the next enabled source.
- Download details under the progress ring: package, version/build, format, ABI,
  size, and whether the transfer used a single stream or multiple connections.

### Changed

- Theme surface tinting now respects the accent's saturation. **Mono** stays
  neutral grey while **Ice** keeps its blue surfaces.
- Uptodown CDN downloads use HTTP/1.1, avoiding HTTP/2 header stalls. DoH checks
  fall back to Google DNS when the AdGuard certificate chain is rejected.
- Retrying failed sources now re-includes the Morphe fallback when an Uptodown,
  APKMirror, or APKPure sub-source failed.
- The custom-scheme HEX field no longer leaves extra scroll space after the
  keyboard is dismissed.
- XAPK manifest/APK mismatches now name the field and both values.

### Fixed

- Back during the VirusTotal result message no longer cancels and deletes a
  finished download.
- Custom scheme scrolling no longer leaves the Settings page out of view.
- Failed Uptodown web downloads no longer retry with “Sources tried: None”.

## [2.0.0-beta.3] - 2026-11-07

Follow-up UI beta with transparent logos, richer themes, and a custom colour
scheme.

### Added

- **Custom colour scheme** in Appearance. Tap the colour-wheel row and the
  Accent card expands downward with a HEX field and Save button. One base HEX
  generates tinted backgrounds, cards, borders, controls, and an accent
  gradient. A valid HEX is required. Requested by @IkoMosiEntertainmentHub in
  [#12](https://github.com/GeneralZyonix/morphe-download-helper/issues/12).
- **Tint backgrounds from accent** in Appearance, on by default. Turn it off to
  keep the neutral blue-black or light surfaces. The custom scheme always keeps
  its tint.
- The Custom scheme swatch is now a **colour wheel**, placed below Material You,
  so it is visually distinct from the solid accent presets.
- **Transparent source logos**, including cleaner APKPure and official F-Droid
  artwork, plus a chain-link icon for the Morphe link source.

### Changed

- The separate **Custom accent HEX** card was merged into the Custom scheme
  editor. Existing custom-accent HEX values carry over.
- Every accent preset now tints backgrounds, cards, borders, and controls, not
  just buttons. Light mode uses stronger tints so schemes read clearly instead
  of washing out to white.
- Card borders are accent-tinted and more visible in dark, light, and AMOLED
  modes. AMOLED keeps true-black backgrounds.
- The **Open Morphe** button now has the external-link box-and-arrow icon.
- The **Manage** button in Download sources is a distinct outlined pill with a
  chevron.

### Fixed

- Custom scheme no longer silently uses a default indigo when no HEX has been
  saved.

## [2.0.0-beta.2] - 2026-11-07

Second UI beta, with validation fixes and backup improvements.

### Added

- Import/export now includes the **download folder** and **retention** setting.
  Custom folder access may need to be re-approved after importing on a new
  device. Requested by @IkoMosiEntertainmentHub in
  [#12](https://github.com/GeneralZyonix/morphe-download-helper/issues/12).
- **Real source logos** in the source lists, shown in the rounded tile style.
  Concept feedback from @thegodfather-hub and @IkoMosiEntertainmentHub in
  [#12](https://github.com/GeneralZyonix/morphe-download-helper/issues/12).
- One **Test all sources** button at the bottom of the Sources card. Individual
  Test buttons were removed so the list stays compact. Each result is still
  saved and shown under its source.
- Rounded, theme-aware dialogs and tinted icon tiles for Settings sections.
  Reported as inconsistent by @IkoMosiEntertainmentHub in
  [#12](https://github.com/GeneralZyonix/morphe-download-helper/issues/12).
- Optional **Faster multi-connection downloads** in Storage. It is off by
  default and falls back to the normal single stream automatically. Requested
  by @thegodfather-hub in
  [#20](https://github.com/GeneralZyonix/morphe-download-helper/issues/20).

### Changed

- The separate **Source order** section is gone. Sources and source order are
  now one list: drag a row by its handle to change the priority. Enabled
  sources stay at the top; disabled sources move to the bottom. Requested by
  @IkoMosiEntertainmentHub in
  [#11](https://github.com/GeneralZyonix/morphe-download-helper/issues/11) and
  [#12](https://github.com/GeneralZyonix/morphe-download-helper/issues/12).
- Main now shows **all enabled sources** instead of stopping after four and
  saying "more sources enabled". This also fixes the missing-source report from
  @IkoMosiEntertainmentHub in
  [#11](https://github.com/GeneralZyonix/morphe-download-helper/issues/11).
- The **Uninstall Helper** button was removed from Main. Suggested by
  @IkoMosiEntertainmentHub in
  [#12](https://github.com/GeneralZyonix/morphe-download-helper/issues/12).
- Multi-connection downloads stay opt-in because testing on a fast Wi-Fi
  connection showed no speed gain over the normal OkHttp stream, and it can be
  slower on some servers.

### Fixed

- XAPK/APKS validation no longer trusts `manifest.json` before reading the
  APKs. Package checks also run for unversioned requests, readable APKs
  override the manifest, and mismatches are rejected. Reported by
  @thegodfather-hub in
  [#21](https://github.com/GeneralZyonix/morphe-download-helper/issues/21).
- Added 10 fixture tests for the split-archive validator.
- Settings and Appearance no longer jump to the top after a theme change.
  Originally reported by @rowi123 in
  [#5](https://github.com/GeneralZyonix/morphe-download-helper/issues/5).

## [2.0.0-beta.1] - 2026-11-07

First public beta of the redesigned interface.

### Added

- New **Appearance** screen with **Dark**, **AMOLED**, **Light**, and
  **Follow system** modes.
- Accent presets: Original, Aurora, Mint, Forest, Gold, Sunset, Rose, Berry,
  Ice, Ocean, and Mono.
- **Material You** accent on Android 12+, using the device wallpaper colours.
- Custom HEX accent with automatic light or dark button text for contrast.
- Live preview and one-tap reset on the Appearance screen.
- Collapsible Settings sections with icons: General, Updates, Download pages,
  Sources, Storage, Security, and Background.
- Redesigned Main screen with a hero action card, compact source list, and a
  direct **Edit order** link.
- Redesigned Download screen with a circular progress ring, live source steps,
  and a clearer failure screen with retry, edit sources, and copy details.
- French translations for the new UI.

### Changed

- **Follow system** is now the default theme. Reset returns to System + Aurora.
- Source order and other settings save immediately; the Done button only closes
  Settings.
- Switches use a vivid checked state and a dimmed unchecked state.
- Settings and Appearance keep their scroll position when the theme changes.
- Dialogs, system bars, switches, progress indicators, and WebView chrome follow
  the selected theme.
- The Download screen hands the APK back to Morphe immediately on success.

### Fixed

- Settings and Appearance jumping to the top after a theme change.
- The Reset appearance card only reacting when its title text was tapped.
- Update action buttons becoming unreadable with light accent colours.

### Notes

- This is a pre-release for testing. Stable remains `v1.6.15`.
- Includes the opt-in **Live AdGuard checks** and the 512 KB log cap from
  `v1.6.16-beta.1`.

## [1.6.16-beta.1] - 2026-11-07

### Added

- Optional **Live AdGuard checks** under **Settings → Download pages**. When the
  bundled list does not know a host, the helper asks AdGuard DNS-over-HTTPS
  whether it is blocked and caches the answer. It is off by default, uses a
  short timeout, fails open, and falls back to the bundled list if AdGuard is
  unavailable. It sends the checked hostnames to AdGuard. Requested in
  [#18](https://github.com/GeneralZyonix/morphe-download-helper/issues/18).
- The helper log is capped at 512 KB and keeps its most recent 128 KB, so a
  long-running install cannot produce an unbounded log file.

## [1.6.15] - 2026-11-07

### Fixed

- Exact build-number validation: when Morphe supplies `VERSION_CODES`, the
  downloaded APK must match one of those `versionCode` values and the requested
  version name.
- APKMirror WebView fallback now selects the exact requested build and falls
  back to the remaining enabled sources if a wrong build is returned.
- Uptodown builds rejected for a wrong build number or a newer `minSdkVersion`
  now fall back to the remaining enabled sources.
- APKMirror prefers the requested CPU ABI and no longer treats every `nodpi`
  row as a universal APK.
- Update checks follow the installed channel: stable builds see only stable
  releases, while pre-release builds can see newer pre-releases.

### Added

- Optional ad and tracker blocking for the in-app download pages.
- Complete French translations for the current UI.

## [1.6.15-beta.5] - 2026-11-07

### Fixed

- APKMirror's WebView fallback now reads the build numbers shown on its version
  page and selects the row matching Morphe's requested build. This covers the
  case where the normal APKMirror request is blocked by Cloudflare and the
  helper has to navigate the page in a WebView.
- If APKMirror still hands over a wrong build, the helper now rejects it and
  retries the remaining enabled sources in the configured order, instead of
  stopping with a failure.
- Reported and confirmed for HushGram in
  [#19](https://github.com/GeneralZyonix/morphe-download-helper/issues/19).

## [1.6.15-beta.4] - 2026-11-07

### Fixed

- Morphe's version codes are now treated as exact build numbers. When a
  request supplies one or more codes, the helper only accepts an APK whose
  `versionCode` matches one of them, and requires the version name to match as
  well. This prevents a same-named build with a different build number from
  being handed to Morphe. Reported in
  [#19](https://github.com/GeneralZyonix/morphe-download-helper/issues/19).
- A Uptodown build rejected for a wrong build number now falls back to the
  remaining sources, just like a build rejected for its minimum Android
  version.
- APKMirror reads the build number from each variant row and prefers the exact
  requested build. If none matches, it stops before downloading a wrong build.
- APKPure, Aptoide, Appteka, and F-Droid source selection also prioritise exact
  build numbers when Morphe supplies them.

## [1.6.15-beta.3] - 2026-11-06

### Fixed

- APKMirror now prefers the requested CPU ABI for plain APK downloads. A
  multi-ABI version page could previously select the first plain APK (for
  example `armeabi-v7a` for an `arm64-v8a` request), download the whole file,
  and only then fail version validation.
- The universal-APK detection no longer treats every `nodpi` row as universal;
  only an explicitly labelled universal build is preferred.

## [1.6.15-beta.2] - 2026-11-06

### Fixed

- Update checks now follow the installed channel. Stable installs still see
  only stable releases; a pre-release install is compared with newer
  pre-releases instead of being told the older stable release is the latest.
  The up-to-date message now shows the installed version.
- Added the complete French translations from
  [#17](https://github.com/GeneralZyonix/morphe-download-helper/pull/17).

## [1.6.15-beta.1] - 2026-11-06

### Added

- Optional ad and tracker blocking for the in-app source pages. A small bundled
  host list stops known ad/tracker requests in the Uptodown, APKMirror, and
  APKPure WebViews, and the overlay hider now runs on all of them. Cloudflare
  Turnstile, reCAPTCHA/hCaptcha, and the download/CDN hosts are allowlisted.
  The setting is under **Settings → Download pages**, enabled by default, and
  can be turned off if a verification page ever misbehaves. This is a content
  filter, not a DNS/VPN: it needs no extra permissions or notification and adds
  no network round trip. Requested in
  [#18](https://github.com/GeneralZyonix/morphe-download-helper/issues/18).

## [1.6.14-beta.2] - 2026-11-06

### Fixed

- Uptodown builds whose `minSdkVersion` is newer than the device are now rejected
  during validation, and the helper automatically retries the remaining sources.
  Android 12's package parser cannot read the base APK of such a bundle, which made
  Morphe show **The selected file is not a valid APK** even though the helper could
  read its manifest. Uptodown's Gboard APKS requires SDK 32; APKPure's build for the
  same version requires SDK 26 and is now used instead. Reported in
  [#15](https://github.com/GeneralZyonix/morphe-download-helper/issues/15).

## [1.6.14-beta.1] - 2026-11-06

### Fixed

- Uptodown **APKS/XAPK** downloads now read the split APKs' binary
  `AndroidManifest.xml` directly when Android's package parser cannot read the base
  split. This fixes Gboard's 44.33 MB package being rejected on Android 12, including
  the split-set format used by Uptodown. Reported in
  [#15](https://github.com/GeneralZyonix/morphe-download-helper/issues/15).

### Changed

- The launcher setting is now **Show app icon**. The launcher alias starts disabled so
  Android does not leave a synthetic icon behind, and the helper can still be opened
  from **Android Settings → Apps → Morphe Download Helper → Open** when the icon is
  hidden. The saved state is restored after updates and reboots. Reported in
  [#14](https://github.com/GeneralZyonix/morphe-download-helper/issues/14).
- Turning off the VirusTotal master switch now hides the API-key, block, upload, quota,
  and help rows, leaving a compact Security card.

## [1.6.13] - 2026-11-06

### Fixed

- Uptodown XAPK downloads now fall back to `manifest.json` for package and version
  validation when the embedded split base APK cannot be parsed by the device. This
  covers Gboard's 44.33 MB XAPK on Android 12. Reported in
  [#13](https://github.com/GeneralZyonix/morphe-download-helper/issues/13).

## [1.6.12] - 2026-11-06

### Fixed

- GitHub release versions written with a leading `v` (for example `v1.0.14`) are now
  treated as the same version as the APK manifest's `1.0.14`.
- When a GitHub APK is rejected because its version or signing certificate does not match
  the installed app, the helper now shows that reason instead of opening the APKPure
  WebView as a last resort.

## [1.6.11] - 2026-11-06

### Added

- **After hand-off** cleanup option under **Settings → Keep downloads**. It skips the
  public Downloads copy, keeps the temporary cache file for a short grace period, and
  removes it on a later launch. Requested by
  [@thegodfather-hub](https://github.com/thegodfather-hub) in
  [#10](https://github.com/GeneralZyonix/morphe-download-helper/issues/10).
- More detailed download diagnostics: device, Android version, ABIs, free storage,
  network type and link speed, final URL, HTTP protocol, CDN headers, TLS details, and
  DNS/connect/TLS/header/body timings. This is intended to isolate the slow-download
  reports in [#7](https://github.com/GeneralZyonix/morphe-download-helper/issues/7).

### Changed

- The default source order is now **Morphe link → APKMirror → APKPure → F-Droid →
  Aptoide → Appteka → GitHub releases**. The APKPure fast path now runs in its configured
  position instead of before the Morphe resolver.
- The APKMirror WebView now hides the fixed **APKMirror Premium** banner. Requested by
  [@thegodfather-hub](https://github.com/thegodfather-hub) in
  [#9](https://github.com/GeneralZyonix/morphe-download-helper/issues/9).

### Fixed

- The **Download sources** list on the main screen and the download screen now use the
  same saved order as the resolver, so they cannot drift from **Settings → Source order**.
  Existing custom orders are preserved, and the old auto-generated APKPure-first order is
  migrated to the new default. Reported by
  [@IkoMosiEntertainmentHub](https://github.com/IkoMosiEntertainmentHub) in
  [#11](https://github.com/GeneralZyonix/morphe-download-helper/issues/11).
- Retention cleanup now also removes expired `Downloads/Morphe Helper` and custom-folder
  copies, not only the internal cache.
- The public settings file is found under its actual `.txt`/`.conf.txt` names, and the
  helper no longer creates a new `helper-settings ... (1).txt` duplicate.
- Restoring the hidden launcher icon with `show_launcher_icon=true` now works with the
  existing `helper-settings.conf.txt` file. Public settings are no longer overwritten or
  deleted when the helper cannot read or update them, and the log records the file found
  and the launcher component state.
- The settings file is preserved by cleanup and **Delete all downloaded APKs**.

## [1.6.10] - 2026-11-06

### Added

- Optional **Hide launcher icon** setting under **Settings → Launcher icon**. It removes
  the app from the launcher without disabling the helper. The setting and confirmation
  warn that Settings and the changelog cannot be reopened afterwards; restoring requires
  uninstalling and reinstalling the APK, or using ADB. Requested by
  [@thegodfather-hub](https://github.com/thegodfather-hub) in
  [#8](https://github.com/GeneralZyonix/morphe-download-helper/issues/8).

## [1.6.9] - 2026-11-06

### Fixed

- The **In-app update popup** setting is now hidden and inactive when **Automatic via
  Shizuku** update mode is selected. Switching back to notification mode restores the
  setting with its saved value.

## [1.6.8] - 2026-11-06

### Added

- Opt-in **In-app update popup** under **Settings → Updates**. When enabled, the helper
  shows a dialog on app launch when an update is available, with **Update**, **Later**,
  and **Skip this version** actions.
- The popup respects the update-check master switch, snooze and skipped-version state,
  and Shizuku automatic-update mode.

## [1.6.7] - 2026-11-06

### Fixed

- Disabling **GitHub releases** now only blocks the automatic GitHub search and remembered
  repositories. GitHub links supplied by Morphe's resolver remain available as part of the
  Morphe fallback source.
- Resolver-supplied GitHub downloads still receive the package, version, and installed-app
  signature checks.

## [1.6.6] - 2026-11-06

### Added

- **GitHub releases** is now a normal source in Settings: it has its own toggle and can
  be reordered alongside the other sources.

### Fixed

- GitHub downloads now verify the package name, version, and signing certificate against
  the installed app when that app is available.
- Disabling **GitHub releases** now blocks remembered repositories, automatic GitHub
  searches, and GitHub links supplied by Morphe's resolver.

## [1.6.5] - 2026-11-05

### Fixed

- VirusTotal uploads no longer abort after a few seconds while the analysis is still
  queued. The helper now waits up to 5 minutes and checks both the analysis and the
  existing file report before continuing.
- Re-uploading a file that VirusTotal is still processing no longer fails with HTTP 409;
  the helper waits for the existing report instead.

### Changed

- The VirusTotal scanning screen now explains that analysis can take several minutes,
  shows a live elapsed-time counter, and offers a visible **Skip scan** button.
- VirusTotal polling now updates once per second and stays within the public
  four-requests-per-minute API limit.

## [1.6.4] - 2026-11-05

### Fixed

- APKMirror downloads no longer stall in the WebView when the Morphe resolver fallback
  is disabled. The WebView now follows search results, release pages, and variant pages
  automatically before pressing the download button.
- APKMirror scroll links such as **Scroll to available downloads** and **Download** are
  no longer mistaken for the final download button.

## [1.6.3] - 2026-11-05

### Fixed

- APKMirror search and app pages that return an HTTP 403 now open in the in-app WebView
  instead of failing immediately, allowing the Cloudflare check to be completed.
- Improved the changelog page: its loading bar now stops after loading, and a version
  selector can show notes for recent releases.

## [1.6.2] - 2026-11-05

### Fixed

- Settings no longer jump back to the top after saving the VirusTotal API key or changing
  another setting that recreates the screen. The current scroll position is saved and
  restored.

## [1.6.1] - 2026-11-05

### Fixed

- Clarified that **VirusTotal checks** is the master switch. Its subtitle and help text now
  explicitly say that turning it off disables all VirusTotal lookups, uploads, quota
  handling, and blocking.
- The language selector now discovers supported languages from `locales_config.xml`, so a
  newly merged translation appears automatically under **Settings → App language**.
- Updated the translation documentation and current language list.

## [1.6] - 2026-11-05

### Added

- Complete French translation and Android string-resource extraction, contributed by
  [@foXaCe](https://github.com/foXaCe) in
  [PR #4](https://github.com/GeneralZyonix/morphe-download-helper/pull/4).
- In-app **App language** selector in Settings for English, French, or the system default
  on Android 13+.
- Translation guide at [TRANSLATING.md](TRANSLATING.md), linked from the README, with the
  English source strings, placeholder rules, and pull-request instructions.
- Optional **VirusTotal hash check** in Settings. When enabled, the helper calculates the
  downloaded file's SHA-256 hash and looks it up through the VirusTotal API before handing
  the APK to Morphe.
- A user-supplied VirusTotal API key setting. By default only the SHA-256 hash is sent,
  and the key stays on the device.
- **Upload unknown files** setting, off by default. If VirusTotal has no report for the
  hash, the APK can be uploaded for analysis. This shares the file with VirusTotal and
  security vendors and is limited to files up to 650 MB.
- **Always upload to VirusTotal** setting, off by default. It skips the hash lookup and
  uploads every downloaded APK, making every download flow longer. VirusTotal documents
  new-file uploads as not consuming API lookup quota, though uploads still share the APK
  and may be rate-limited.
- **Upload if quota exceeded** setting, off by default. When a hash lookup hits the API
  quota, the helper can try the upload path instead of aborting or continuing unscanned.
- VirusTotal uploads now show a percentage while sending, followed by an analysis-waiting
  status, so large uploads no longer look stuck.
- Pressing Back during a VirusTotal upload now skips the scan and continues the handoff to
  Morphe instead of cancelling the entire download.
- VirusTotal results now remain on screen for five seconds before the handoff continues.
  A still-pending analysis aborts the handoff rather than passing on an unscanned file.
- **Block detected files** toggle. When enabled, files with VirusTotal detections are
  deleted instead of being returned. Unknown hashes, missing keys, and lookup errors fail
  open and allow the download.
- VirusTotal public API quota guidance in Settings: about 4 requests/minute and 500/day,
  with 32 MB direct uploads or 650 MB through VirusTotal's upload URL. If the API reports a
  quota limit or denied key, the message is shown to the user.
- **Quota exceeded** setting: choose **Continue unscanned** (default) or abort the handoff
  to Morphe when VirusTotal reports that the API quota has been exceeded.

### Contributors

Huge thanks to [@foXaCe](https://github.com/foXaCe) for the excellent localization work in
[PR #4](https://github.com/GeneralZyonix/morphe-download-helper/pull/4): extracting the UI
strings, adding the French translation, and making the diagnostics, source names, Shizuku
states, and error messages translatable. This release would not have its language support
without that contribution.

## [1.5.2] - 2026-11-04

### Fixed

- When Morphe's resolver points to a GitHub release, that release is now treated as the
  patch source's intended source and used immediately. This fixes patch sources such as
  kveld9's Brave patches, which require the GitHub standalone APK even when APKPure has the
  same version as an XAPK.
- APKPure's same-version XAPK is skipped for plain-APK requests when GitHub is the intended
  source, preventing a successful download that would later fail patching.
- Aptoide and other generic sources are bypassed when the resolver explicitly selects a
  GitHub release for the patch source.

### Changed

- GitHub is still the automatic final fallback for normal requests. The resolver pointing
  at GitHub is the signal that GitHub should be used first for that request.
- Updated the README and Reddit release notes to explain the distinction.

## [1.5.1] - 2026-11-04

### Fixed

- Installed-app version codes are now reused only when the installed version name matches
  Morphe's requested version. This prevents downloading an older build such as Brave 1.92.140
  when the patch requests 1.98.62.
- Split archives are validated against the requested package and version. A wrong XAPK can
  no longer reach Morphe just because split-archive validation was previously skipped.
- APKPure no longer retries its alternate APK/XAPK endpoint after a version mismatch, which
  removes the duplicate large downloads users saw.
- GitHub release assets now prefer the requested ABI, so Brave downloads
  `BraveMonoarm64.apk` instead of a universal build.

### Changed

- GitHub releases are now an automatic last resort. After the normal APK sources fail, the
  helper uses the remembered repository or searches GitHub automatically.
- The manual **Search GitHub releases** and **This app is on GitHub** buttons have been
  removed because GitHub is automatic now.
- GitHub repositories detected from Morphe's resolver are remembered automatically. The
  helper stores the repository, not a fixed APK, and fetches the matching release each time.
- The changelog and README now describe the automatic GitHub behavior.

## [1.5] - 2026-11-03

### Added

- Built-in update checker for new GitHub releases, checked on launch at most every six hours.
- **Updates** settings card with a check toggle, manual check, update mode, Shizuku status,
  and a changelog button.
- **Notify me when available** mode: a single notification opens an update screen with the
  version, release notes, and installation choices.
- **Automatically update via Shizuku** mode: the helper checks, downloads, and installs a
  new release silently when Shizuku is installed, running, and approved.
- Completion notification after a successful automatic self-update: “Morphe Download Helper
  was updated to version X.” Android may close the helper while replacing its own APK, so a
  delayed check confirms the installed version before posting this.
- Update choices in the update screen: **Install normally**, **Install via Shizuku** (when
  Shizuku is ready), **Remind me later** (24 hours), and **Skip this version**.
- Battery optimization checker in Settings, with a green **Unrestricted ✓** state and a
  request-exemption action when Android may delay background checks or notifications.
- Download folder choices: **Downloads/Morphe Helper** by default on Android 10+, internal
  app cache, app external storage, or a custom folder chosen with Android’s folder picker.
- Update APKs are staged in `Download/Morphe Helper/updates` and cleaned up after install.
- **GitHub release downloads**: an optional **Search GitHub releases** fallback for apps
  distributed through GitHub instead of regular APK sites. Successful repositories are
  remembered and can be managed from Settings.

### GitHub downloads

- When the normal sources fail, the failure screen offers **Search GitHub releases**.
- The helper searches GitHub repositories by app name or package, then checks release tags
  for the requested version.
- It accepts APK, APKM, APKS, XAPK, and other APK-family release assets, preferring the
  format Morphe requested.
- The downloaded file is still checked against the requested package and version before it
  is handed back to Morphe.
- A successful repository is remembered in **Settings → GitHub apps** and is tried first on
  the next request for that package. It can be removed with the **X** button if the app
  becomes available on a regular source.
- A **This app is on GitHub** shortcut is also available in the fallback WebView area.

### Update choices explained

- **Notify me when available** — the helper posts one notification. Tapping it opens the
  update screen, where you can read the changelog and choose normally, via Shizuku, remind
  later, or skip this version. This is the default and works without Shizuku.
- **Automatically update via Shizuku** — the helper installs new releases silently after
  Shizuku permission is granted. If Shizuku is unavailable, the helper falls back to the
  notify flow so an update is never silently missed.
- **Install normally** — opens Android’s package installer. This is the safest choice if
  you do not use Shizuku or want to confirm the install manually.
- **Install via Shizuku** — installs without the normal package-installer prompts, but only
  appears when Shizuku is ready.
- **Remind me later** — hides the update for 24 hours, then checks again.
- **Skip this version** — suppresses notifications for that release until a newer one appears.
- Turning off **Check for updates** disables both notification and automatic update checks.

### Changed

- Update notifications are now a single tap target instead of three small action buttons.
- The default download folder is `Downloads/Morphe Helper` on Android 10 and newer; older
  devices keep the internal default until public storage permission is granted.
- The update screen uses navigation-bar insets, so its buttons never sit inside the gesture
  or three-button navigation bar.

### Fixed

- Shizuku updates now copy the staged APK to a location readable by Android’s package
  manager, fixing installs that previously failed with exit code 255.
- Update installation works with the new public Downloads staging area and cleans up staged
  files on the next helper launch if the process was closed during an update.

### Notes

- Automatic updates require the Shizuku app, Shizuku running, and helper permission. No root
  is required.
- The update checker uses GitHub’s latest-release API and requires a release asset ending in
  `.apk`. The universal APK is preferred when present.
- Battery optimization exemptions are requested through Android’s standard system dialog;
  they can be revoked at any time in Android settings.

## [1.4] - 2026-11-02

### Added

- **Appteka** source for SnapTube, clearly marked with a hazard warning.
- SnapTube-only Appteka scope by default, with an opt-in to use Appteka as an extra source
  for all apps.
- Settings for download folder, retention cleanup, and deleting downloaded APKs.
- Source order controls with move up/down and reset to default.

### Changed

- APKM/APKS requests use APKMirror first, with an APKPure XAPK fallback.
- Settings now combines storage, source order, source toggles, and the Appteka warning.

### Fixed

- Uptodown pages that provide a plain APK now save it with the correct `.apk` extension
  instead of the requested split-archive extension.
- Appteka downloads resolve the exact SnapTube version and version code.

### Notes

- Appteka hosts modded APKs. It is limited to SnapTube by default, and using it for other
  apps is not recommended because those builds may contain altered code.

## [1.3] - 2026-11-01

### Added

- Morphe resolver result cache for three hours, plus a four-second resolver timeout.
- Clearer APKMirror progress states: page, download key, and starting download.

### Changed

- APKPure is tried first when Morphe supplies a numeric `versionCode`.
- If Morphe supplies no `versionCode`, the Morphe resolver is followed first.
- APKPure WebView is now the last resort, so a broken page cannot block APKMirror or the resolver.
- Source list order and Source settings now show APKPure → Morphe link → APKMirror → fallbacks.
- README clarifies the Morphe resolver link versus the patch-bundle fallback URL.
- Settings and Visual guide use navigation-bar insets, so bottom buttons clear both gesture and 3-button navigation.

### Notes

- Apps with a versionCode should start faster through APKPure.
- Requests without a versionCode may still take longer because the helper follows Morphe’s resolver page first, by design.
## [1.2] - 2026-11-01

### Added

- Installed-app `versionCode` fallback for requests that omit `VERSION_CODES`.
- APKPure mobile API resolution, with a direct-asset fast path before the WebView fallback.
- Pause and Resume controls for active downloads.
- Source labels above the progress bar, plus download speed, ETA, and resumable partial downloads.
- Foreground download notification so long downloads are less likely to be killed.
- Per-source **Test** buttons and last-known source health in Source settings.
- Granular status updates: installed version, Morphe resolver, source lookup, and download start.
- **About** section with the GeneralZyonix logo and links to GitHub, Releases, and Reddit.
- In-app **Visual guide** with the setup screenshots.
- Adaptive content width for tablets.
- `IDEAS.md` backlog for deferred features.

### Changed

- Moved Uptodown/APKPure browser-fallback warnings from the main screen into Source settings.
- APKPure WebView fallback no longer loops on repeated page loads or ad overlays.
- APKMirror retries only transient rate-limit/server errors, with backoff.
- APKPure direct download is attempted first when a numeric versionCode is available.

### Notes

- Resolution can take a little longer for some apps now. The helper performs extra checks
  (installed versionCode, Morphe resolver, APKPure API, source health, retries, and
  fallbacks) before downloading. This is intentional: it makes the flow more robust and
  less likely to break when a source changes or omits metadata.

## [1.1] - 2026-10-30

### Added

- Morphe resolver API support, so the helper follows the same download page Morphe's own
  dialog uses.
- Uptodown support through an inline WebView verification page, including an automatic
  refresh if the download button stays stuck.
- APKPure WebView fallback when a direct APKPure request cannot be resolved.
- XAPK/APKS awareness: split archives are accepted when Morphe sends
  `ALLOW_SPLIT_ARCHIVE=true`.
- F-Droid and Aptoide as additional download sources.
- Source settings screen for enabling or disabling individual download sources.

### Changed

- APKPure now uses the numeric `versionCode` supplied by Morphe. This fixes the previous
  `HTTP 403` responses and downloads the exact requested build.
- Replaced the launcher icon with the **Orbit Download** mark: cyan -> blue -> purple ->
  magenta ring, download arrow, and a small M orb in the GeneralZyonix palette.

## [1.0] - 2026-10-30

### Added

- First public release of **Morphe Download Helper**.
- Implements Morphe's `app.morphe.manager.action.DOWNLOAD_ORIGINAL_APK` helper contract.
- Tries the patch bundle's fallback link first, then APKMirror, then APKPure.
- Returns a `content://` URI with read permission so Morphe can validate the APK.
- Dark modern UI with an indigo "M + download arrow" logo and launcher icon.
- Download sources card showing the resolution order.
- Clear warnings that Uptodown is unsupported and that automatic resolution is best-effort.
- Local diagnostic log with **Share log** and **Clear log** actions.
- Illustrated setup guide for enabling and trusting the helper in Morphe.
- Universal APK for Android 6.0+ (arm64-v8a, armeabi-v7a, x86, x86_64).




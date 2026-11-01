# Changelog

All notable changes to Morphe Download Helper are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a simple `MAJOR.MINOR` version scheme.

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



# Changelog

All notable changes to Morphe Download Helper are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a simple `MAJOR.MINOR` version scheme.

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

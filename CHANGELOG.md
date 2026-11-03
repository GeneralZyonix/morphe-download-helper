# Changelog

All notable changes to Morphe Download Helper are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a simple `MAJOR.MINOR` version scheme.

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




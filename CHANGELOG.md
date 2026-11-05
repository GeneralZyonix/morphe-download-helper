# Changelog

All notable changes to Morphe Download Helper are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a simple `MAJOR.MINOR` version scheme.

## [Unreleased]

### Added

- Optional **VirusTotal hash check** in Settings. When enabled, the helper calculates the
  downloaded file's SHA-256 hash and looks it up through the VirusTotal API before handing
  the APK to Morphe.
- A user-supplied VirusTotal API key setting. The APK is never uploaded; only the hash is
  sent, and the key stays on the device.
- **Block detected files** toggle. When enabled, files with VirusTotal detections are
  deleted instead of being returned. Unknown hashes, missing keys, and lookup errors fail
  open and allow the download.
- VirusTotal public API quota guidance in Settings: about 4 lookups/minute, 500/day, and
  15,500/month. If the API reports a quota limit or denied key, the message is shown to the
  user.
- **Quota exceeded** setting: choose **Continue unscanned** (default) or abort the handoff
  to Morphe when VirusTotal reports that the API quota has been exceeded.

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




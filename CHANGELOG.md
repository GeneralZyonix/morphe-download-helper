# Changelog

All notable changes to Morphe Download Helper are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a simple `MAJOR.MINOR` version scheme.

## [Unreleased]

## [1.13] - 2026-10-30

### Added

- Download sources card showing the resolution order: Morphe fallback link, APKMirror, APKPure.
- Clear warning that Uptodown is not supported and that automatic resolution is best-effort.
- In-app screenshots and a download section in the README.
- GitHub release with a universal APK for Android 6.0+.

### Changed

- Renamed the app to **Morphe Download Helper** throughout the UI and launcher.
- Cleaned up and documented the Java sources, manifest, Gradle files, and resources.
- Added open-source project files: README, MIT license, `.gitignore`, `.gitattributes`,
  Gradle wrapper, and `local.properties.example`.

### Fixed

- Increased top spacing so the header is not clipped by the status bar.
- Hid the progress bar when a request fails or has no package name.

## [1.12] - 2026-10-30

### Added

- Amber warning card explaining Uptodown limitations and the Morphe default-button fallback.
- Warning row with hazard icon on the download screen.

### Changed

- Renamed the launcher label to **Morphe Download Helper**.
- Switched to a dark app theme to avoid light-theme flashes.

### Fixed

- Added extra top padding so the title clears the status bar.

## [1.11] - 2026-10-30

### Added

- Modern dark UI with gradient background, rounded cards, status chip, and styled buttons.
- New indigo "M + download arrow" logo and launcher icon.

### Fixed

- Restyled the download screen to match the launcher.
- Hid the progress bar on error states.

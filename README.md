# Morphe Download Helper

An unofficial Android helper app that downloads the original APK requested by
[Morphe Manager](https://github.com/MorpheApp/morphe-manager) and hands it back through
Morphe's open `DOWNLOAD_ORIGINAL_APK` intent contract.

> **Unofficial:** this project is not affiliated with, endorsed by, or maintained by the
> Morphe project. Morphe ships no helper of its own and does not endorse any helper.

## Features

- Receives the package, version, version codes, file type, ABIs, and fallback URL from Morphe.
- Tries the patch bundle's fallback link first.
- Falls back to APKMirror, then APKPure.
- Returns a `content://` URI with read permission so Morphe can validate and use the file.
- Shows a dark, modern UI with a source list and clear limitations.
- Keeps a local diagnostic log that can be shared from the app.

## How it works

Morphe sends an intent with the action
`app.morphe.manager.action.DOWNLOAD_ORIGINAL_APK`. The helper:

1. Reads the request extras.
2. Tries the fallback URL from the patch bundle.
3. Searches APKMirror by package name and requested version.
4. Tries APKPure as a last resort.
5. Returns `RESULT_OK`, a `content://` URI, and `FLAG_GRANT_READ_URI_PERMISSION`.

Morphe then re-checks the package name, version, and signature before using the file.
The helper never installs anything.

## Download sources

| Order | Source | Notes |
| --- | --- | --- |
| 1 | Morphe fallback link | Supplied by the patch bundle; may be a direct file or APKMirror page. |
| 2 | APKMirror | Search by package name and requested version. |
| 3 | APKPure | Last resort; supports APK and split-archive endpoints. |
| — | Uptodown | Disabled because its download endpoint requires a Cloudflare Turnstile token. |

If no downloadable APK is found, the helper returns `RESULT_CANCELED` and Morphe shows
its own default download button.

## Limitations

- Only apps with an available APK on the supported sources can be resolved automatically.
- Uptodown is not supported.
- APKMirror/APKPure can change their HTML or block automated requests at any time.
- The helper does not bypass paid apps, license checks, account restrictions, or DRM.
- Automatic downloads may be restricted by the APK sites' terms of service. Use the helper
  responsibly and only download apps you are allowed to access.

## Build

Requirements:

- JDK 21
- Android SDK Platform 35
- Gradle 8.9 (the included wrapper handles this)

Set `sdk.dir` in `local.properties` (or set `ANDROID_HOME`), then run:

```bash
./gradlew :app:assembleDebug
```

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open Morphe → Settings → APK download helper, trust **Morphe Download Helper**, and
use **Use a helper app** during patching.

## Privacy

- No analytics, telemetry, or accounts.
- Downloads go directly from the APK source to the device.
- The diagnostic log stays in the app-specific storage:
  `Android/data/be.alterra.morphehelper/files/logs/helper.log`
- Logs are only shared when the user taps **Share log**.

## Project layout

```text
app/src/main/java/be/alterra/morphehelper/
  MainActivity.java      Launcher screen, source list, log actions
  DownloadActivity.java  Morphe intent contract and APK resolution
  HelperLog.java         Local append-only diagnostic log
app/src/main/res/
  drawable/              Logo and warning icon
  values/                Strings and dark theme
  xml/file_paths.xml     FileProvider paths
```

## Contributing

Issues and pull requests are welcome. Please keep source parsers small and well commented,
and test changes against a real Morphe request before opening a PR.

## License

MIT — see [LICENSE](LICENSE).

## Disclaimer

This project is provided as-is. The authors are not responsible for how it is used. Only
download applications you are legally allowed to access, and respect the terms of service
of the APK sources listed above.

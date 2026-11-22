# Contributing

Thanks for helping improve Morphe Download Helper.

## Supporting the project

If you find this useful, you can buy me a coffee:

https://buymeacoffee.com/generalzyonix

## Before you start

- Search the existing issues before opening a new one.
- Keep changes focused. Separate unrelated fixes into separate pull requests.
- Do not include API keys, tokens, passwords, or personal data in code, logs, or
  screenshots.
- New user-facing strings go in `app/src/main/res/values/strings.xml` in English.
  See [TRANSLATING.md](TRANSLATING.md) for the translation workflow.

## Building and testing

Use the Gradle wrapper from the repository root:

```powershell
.\gradlew.bat :app:assembleIconDebug
.\gradlew.bat :app:testIconDebugUnitTest :app:testNoiconDebugUnitTest
```

Instrumentation tests need an emulator or device:

```powershell
.\gradlew.bat :app:connectedIconDebugAndroidTest
```

Both product flavors (`icon` and `noicon`) should build. Release builds are signed
with the debug key for sideloading.

## Pull requests

- Describe the user-visible change and how you tested it.
- Include logs or screenshots when they help explain a bug.
- Do not bump the version or create a release; the maintainer handles releases.
- For source changes, explain which site or API is involved and what happens when
  it is unavailable.

## Reporting bugs

Use the bug report template and include:

- Helper version, Android version, and device.
- Morphe version and the app/version being fetched.
- The relevant part of the sanitized helper log.
- Whether the request came from a resolver link, a source, or a manual import.

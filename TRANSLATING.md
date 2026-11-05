# Translating Morphe Download Helper

The English source strings are in
[app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml).

A French example is available in
[app/src/main/res/values-fr/strings.xml](app/src/main/res/values-fr/strings.xml).

## Add a language

1. Fork the repository and create a branch.
2. Copy `app/src/main/res/values/strings.xml` to
   `app/src/main/res/values-<language-code>/strings.xml`.
   For example, German uses `values-de/strings.xml`.
3. Translate only the text between the XML tags.
4. Keep every `name="..."` key unchanged.
5. Keep placeholders unchanged: `%1$s`, `%2$s`, `%d`, `%1$d`, and similar tokens.
6. Do not translate the product name `Morphe Download Helper`.
7. Leave UI labels quoted from Morphe unchanged when they refer to Morphe’s own UI,
   such as `APK download helper`, `Use a helper app`, and `Open helper right away`.
8. Build and check the result:

```powershell
.\gradlew.bat :app:assembleDebug
```

9. Open a pull request containing only the new `values-<language-code>` directory.

## Importing a translation

Maintainers can merge the new resource directory directly. No Java changes are needed.
Please verify that:

- every key exists in both the English and translated files,
- placeholders are unchanged,
- plurals still have the correct quantity variants,
- the app builds successfully.

## Current translations

| Language | File | Status |
| --- | --- | --- |
| English | `values/strings.xml` | Source |
| French | `values-fr/strings.xml` | Test translation in PR #4 |

The French translation now covers the VirusTotal settings and upload-progress messages.

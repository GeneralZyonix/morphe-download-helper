# Translating Morphe Download Helper

The English source strings are in
[app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml).

A French example is available in
[app/src/main/res/values-fr/strings.xml](app/src/main/res/values-fr/strings.xml).

New strings are added in English only. Translations are community-maintained: missing
translations fall back to English until a contributor submits them. Please do not
auto-translate new strings in pull requests.

## Add a language

1. Fork the repository and create a branch.
2. Copy `app/src/main/res/values/strings.xml` to
   `app/src/main/res/values-<language-code>/strings.xml`.
   For example, German uses `values-de/strings.xml`.
3. Add metadata above `<resources>` so we know what was translated and who to credit:

```xml
<!-- Language: German (de) -->
<!-- Translator: @your-github-name -->
```

4. Add the language code to
   `app/src/main/res/xml/locales_config.xml`:

```xml
<locale android:name="de" />
```

   This also makes it appear in **Settings → App language** automatically.
5. Translate only the text between the XML tags.
6. Keep every `name="..."` key unchanged.
7. Keep placeholders unchanged: `%1$s`, `%2$s`, `%d`, `%1$d`, and similar tokens.
8. Do not translate the product name `Morphe Download Helper`.
9. Leave UI labels quoted from Morphe unchanged when they refer to Morphe's own UI,
   such as `APK download helper`, `Use a helper app`, and `Open helper right away`.
10. Build and check the result:

```powershell
.\gradlew.bat :app:assembleDebug
```

11. Open a pull request containing the new `values-<language-code>` directory and the
    `locales_config.xml` entry.

## Importing a translation

Maintainers can merge the new resource directory and its `locales_config.xml` entry
directly. No Java changes are needed for the language to appear in the selector.

Please verify that:

- every key exists in both the English and translated files,
- placeholders are unchanged,
- plurals still have the correct quantity variants,
- the language is listed in `locales_config.xml`,
- the app builds successfully.

## Current translations

| Language | File | Status |
| --- | --- | --- |
| English | `values/strings.xml` | Source |
| French | `values-fr/strings.xml` | Contributed by @foXaCe |

The French translation covers the VirusTotal settings and upload-progress messages too.

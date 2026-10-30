# Releasing

This project uses a simple `MAJOR.MINOR` version and publishes a universal APK on GitHub
Releases. Do **not** bump the version unless the maintainer explicitly asks for a version
bump.

## 1. Choose the version

- If a target version was given, use it.
- Otherwise bump the minor version: `1.0` → `1.1`.
- Increment `versionCode` by 1.

## 2. Update the version

Edit `app/build.gradle`:

```gradle
versionCode 2
versionName "1.1"
```

## 3. Update the changelog

Move the relevant items from `## [Unreleased]` in `CHANGELOG.md` into a new section:

```markdown
## [1.1] - 2026-10-30
```

Add a short, user-facing summary of what changed.

## 4. Build the APK

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The app has no native libraries, so this single APK is universal for arm64-v8a,
armeabi-v7a, x86, and x86_64. Do not create per-ABI APKs.

## 5. Commit and push

```powershell
git add app/build.gradle CHANGELOG.md
git commit -m "Release v1.1"
git push
```

## 6. Create the GitHub release and upload the APK

Copy the APK to a release-friendly name:

```powershell
Copy-Item app/build/outputs/apk/debug/app-debug.apk `
  "MorpheDownloadHelper-v1.1-universal.apk"
```

Create the release, using the new changelog section as the notes:

```powershell
gh release create v1.1 "MorpheDownloadHelper-v1.1-universal.apk" `
  --title "v1.1 - Universal APK" `
  --notes "Paste the 1.1 changelog section here" `
  --latest
```

If `gh` is not on `PATH`, use:

```powershell
& "C:\Program Files\GitHub CLI\gh.exe" release create ...
```

## 7. Verify

```powershell
gh release view v1.1 --json url,assets
```

Confirm the release is public and the APK asset is uploaded.

## Signing note

Current release APKs are signed with the Android debug key so they can be sideloaded. If a
proper release keystore is added later, switch to `:app:assembleRelease`, keep the keystore
outside the repository, and load its passwords from an untracked `keystore.properties` or
environment variables.

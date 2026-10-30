# Agent instructions

## Version bumps and releases

- **Never bump the version automatically.** Only do it when the user explicitly says
  "version bump" or gives a target version.
- When the user asks for a version bump, follow `RELEASING.md` exactly:
  1. Bump `versionCode` and `versionName` in `app/build.gradle`.
  2. Update `CHANGELOG.md` with the user-facing changes.
  3. Build the universal debug APK.
  4. Commit and push the version bump and changelog.
  5. Create a new GitHub release and upload the APK.
- If no target version is given, increment the minor version (`1.0` → `1.1`) and
  increment `versionCode` by 1.
- Do not create per-ABI APKs. The app has no native libraries; one universal APK covers all
  supported devices.
- Current APKs are debug-signed. Do not commit keystores, passwords, or tokens.

## Repository

- GitHub: `GeneralZyonix/morphe-download-helper`
- Default branch: `main`
- Package: `be.alterra.morphehelper`
- App name: `Morphe Download Helper`

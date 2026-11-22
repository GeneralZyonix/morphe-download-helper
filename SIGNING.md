# Signing key migration

**Builds before 3.3.0-rc.3 were signed with temporary GitHub Actions debug keys.**
Each CI run generated a different key, so Android refuses to install a newer
build over an older one.

3.3.0-rc.3 is the first build signed with the permanent release key. Android
treats it as a different app for update purposes, so **one uninstall is required**.
After that, future updates install in place normally.

## Before uninstalling

1. Open Morphe Download Helper.
2. Go to **Settings → Backup → Export configuration**.
3. Choose **without secrets**, or **with secrets** if you want to keep your
   VirusTotal API key, and save the file somewhere outside the app.
4. If you use the public settings file, make sure
   `Downloads/Morphe Helper/helper-settings.txt` exists. It is applied
   automatically after the reinstall.

Download history and cached APKs are removed by the uninstall. Your Morphe
Manager installation and patches are not affected.

## Reinstall

1. Uninstall **Morphe Download Helper**.
2. Install the new APK from the
   [Releases page](https://github.com/GeneralZyonix/morphe-download-helper/releases):
   - **Icon** build if you want a launcher icon.
   - **No-icon** build if you open the helper from Morphe or Android Settings.
3. Open the helper once. If `helper-settings.txt` is present it is applied
   automatically; otherwise use **Settings → Backup → Import configuration**.
4. If you use automatic Shizuku updates, check that Shizuku still lists the
   helper as approved; grant it again if Android asks.

## After the migration

- Future releases use the same permanent signing key, so updates install in
  place.
- The icon and no-icon builds share the same package and key; switching between
  them still works.
- A debug build made on a developer machine still uses a separate debug key and
  cannot update over a release build. That is expected.

## For the first stable release

This notice must also appear in the **3.3.0 stable** release notes and any
release notes published before the migration is complete. Stable users on 3.2.1
cannot update in place and need the same export → uninstall → install → restore
steps.

The signing keystore and passwords are private release material. They are not
committed to the repository and must be backed up outside it.

# Releasing an update

GitHub builds and publishes the signed APK (`.github/workflows/release.yml`), so an update can be
made from any device: a browser, Claude Code on the web or mobile, or any computer with the repo
cloned. No local build is needed.

## Publish an update

1. Make the change and raise both numbers in `app/build.gradle.kts`:
   `versionCode` (+1) and `versionName` (for example 2.1.8 → 2.1.9).
2. Push to `main`. The commit title becomes the release notes shown in the app's update prompt.
3. The **Release APK** workflow (Actions tab) builds the APK (about 20–30 minutes), checks it is
   signed with the app's own key, then commits `release/IPTVPlayer-V2-2.0.0.apk` and
   `release/update.json`.
4. Devices pick it up through the in-app updater, or with Downloader code 7935478.

Pushes that don't raise `versionCode` build nothing. To build without publishing (for example
to test), use **Actions → Release APK → Run workflow**. The APK is kept as a download on the run's
page for 14 days, and it is only published if the version is new. Run workflow also has an
optional release-notes box.

## One-time setup: the signing secrets

Every update must be signed with the same key as the installed app, or Android refuses to install
it. The key lives only in `keystore/iptv-v2-release.jks` and `keystore.properties` on the original
PC (both gitignored). **Keep a backup of both files somewhere safe.**

GitHub needs four repository secrets: **Settings → Secrets and variables → Actions → New
repository secret**.

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file, base64-encoded (command below) |
| `KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `KEY_ALIAS` | `keyAlias` from `keystore.properties` |
| `KEY_PASSWORD` | `keyPassword` from `keystore.properties` |

To copy the keystore as base64 to the clipboard, run this in PowerShell on the PC that has it, then
paste it as the value of `KEYSTORE_BASE64`:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Android Studio\IPTV Player Version 2\keystore\iptv-v2-release.jks")) | Set-Clipboard
```

Check the setup with **Actions → Release APK → Run workflow**. A green run means the secrets work.
The workflow refuses to publish an APK signed with any other key: the expected certificate
fingerprint is `EXPECTED_CERT_SHA256` in the workflow.

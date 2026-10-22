# Eye Guardian

Eye Guardian is an offline Android companion for healthier screen habits. It combines on-device viewing-distance checks, optional app-usage summaries, gentle break reminders, local reports, and small focus games.

## What is here

- **Distance protection** — front-camera analysis stays on the phone; frames are not saved.
- **Activity dashboard** — foreground-app durations and local daily patterns, without message or page-content access.
- **Break engine** — long-session and 20/20 reminders with notification actions.
- **Care history and rewards** — local XP, streaks, Focus Trail scores, unlockable balls, and recommendations.
- **Reports** — user-triggered PDF/JPEG exports for a selected period.

The project is currently an open-source GitHub test release. It is not medical advice, a diagnosis, or a replacement for professional eye care.

## Install the test release

Download the APK attached to the newest GitHub Release, then allow Android to install from that source when prompted. Releases are signed build artifacts; do not install an APK whose checksum is not published with the release.

## Build locally

From `Android/`:

```text
./gradlew testDebugUnitTest lintDebug assembleInstallable
```

`installable` is a directly installable development build. The `release` variant is minified and must be signed with a release key before distribution. Never commit a keystore, passwords, `local.properties`, or signing secrets.

## Versioning a GitHub release

1. Update `versionCode` and `versionName` in `Android/app/build.gradle.kts`.
2. Run the tests, lint, and a clean release build with the maintainer's private signing key.
3. Create an annotated tag such as `v0.1.0` and a GitHub Release with the APK, SHA-256 checksum, supported Android versions, and a short change list.
4. Keep the signing key unchanged for future updates. A different key cannot update an installed Android app.

The CI workflow runs tests, lint, and an installable test APK. It does not contain a production signing secret and must not be treated as the Play Store artifact.

The tag workflow publishes a release only after you configure the repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The keystore must be backed up securely and kept unchanged for all future updates.

## Permissions and data

Permissions are optional and feature-specific. Camera data is analyzed on-device. Usage access is used for aggregated foreground-app durations. Notifications are used for reminders. Profile details, history, settings, crash diagnostics, and reports stay in the app's private local storage unless the user explicitly exports a report.

Read the [Privacy Policy](PRIVACY.md) and the [release checklist](RELEASE_CHECKLIST.md) before installing or publishing a build.

## Development status

The GitHub phase is for developer and tester feedback. Before Play Store submission, replace the maintainer placeholders in `PRIVACY.md`, publish that policy at a stable public HTTPS URL, complete Play Console Data safety declarations, review every dependency, and run the full device matrix in `RELEASE_CHECKLIST.md`.

## License

Eye Guardian is released under the [MIT License](LICENSE). Third-party libraries retain their own licenses; see their respective notices and dependency metadata.

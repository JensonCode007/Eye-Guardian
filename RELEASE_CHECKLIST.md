# Release checklist

## GitHub test release

- [ ] Replace the privacy-contact placeholder and publish `PRIVACY.md` at a stable HTTPS URL.
- [ ] Confirm the repository contains no keystore, signing password, personal data, APK dump, or `local.properties`.
- [ ] Run `testDebugUnitTest`, `lintDebug`, and `assembleInstallable`.
- [ ] Attach the signed APK and SHA-256 checksum to a versioned GitHub Release.
- [ ] Record version name, version code, commit SHA, target SDK, and known limitations in release notes.
- [ ] Ask testers to report Android version, device model, permission state, and reproduction steps—never screenshots containing personal data.

## Safety and privacy gate before Play Store

- [ ] Audit the merged manifest and every dependency; confirm no network permission or network code is present in the offline build.
- [ ] Complete Google Play Data safety, permissions declarations, content rating, and app-access disclosures from the exact release binary.
- [ ] Provide the privacy policy in Play Console and in-app.
- [ ] Confirm prominent disclosure appears before camera, usage-access, notification, and background-protection requests.
- [ ] Confirm camera frames are not persisted and that background camera protection has a visible Android camera indicator.
- [ ] Verify clear-data behavior and uninstall/restore behavior on a clean device.
- [ ] Review the medical disclaimer and avoid claims of diagnosis, treatment, prevention, or guaranteed eye health.

## Device matrix

- [ ] Fresh install and upgrade from the previous release.
- [ ] Android 8, Android 12/13, and the latest supported Android version.
- [ ] Light/dark theme, small and large display, font scaling, rotation, and talkback/basic accessibility checks.
- [ ] Reboot, battery optimization on/off, denied permissions, restricted settings, notification actions, custom sounds, and camera service recovery.
- [ ] Verify report period limits, exact minute totals, empty-day behavior, and no future hours.
- [ ] Verify Focus Trail levels, ball unlocks, progression, score persistence, and no duplicate rewards.
- [ ] Verify the app remains usable when usage access, camera, notification, or battery exemptions are denied.

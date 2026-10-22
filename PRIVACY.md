# Eye Guardian Privacy Policy

**Last updated:** 22 October 2026  
**App:** Eye Guardian  
**Privacy contact:** Replace this line with the maintainer's real email address before a public or Play Store release.

This policy describes the current GitHub test build. It must be reviewed against the exact shipped binary, dependencies, and the jurisdictions where the app is offered. It is not legal advice.

## Summary

Eye Guardian is designed to work offline. The app has no account, advertising, analytics, remote crash service, or cloud sync. The app does not sell or share the information described below with the developer.

## Information used on the device

- **Camera frames:** When distance protection is active and camera permission is granted, frames are analyzed on-device to estimate whether a face is too close. Frames, images, and videos are not saved or uploaded.
- **App usage summaries:** If the user grants Android usage access, the app reads aggregated foreground-app durations and app labels to produce local summaries. It does not read messages, notification contents, web pages, keystrokes, or in-app activity.
- **Profile details:** A name and age may be entered voluntarily for a locally generated report. They are not required for protection features.
- **Care history:** Distance-warning timestamps, daily usage summaries, break/reset events, game results, XP, streaks, settings, and selected notification-sound references are stored locally to power the app.
- **Crash diagnostics:** If the app crashes, a short stack trace may be written to the app-private `crash_logs` folder. These logs are never uploaded and can be cleared from Settings or by clearing app data.

## Notifications and sounds

Notifications are generated locally for distance, break, and 20/20 reminders. If the user selects a custom sound, the app stores the selected Android content URI locally and uses it for the app's notification channels. Android may require the user to change a channel's sound in system settings.

## Reports and exports

PDF and JPEG reports are created only after the user requests an export and chooses a destination through Android's document/file picker. The selected destination is controlled by the user. The app does not upload exported files.

## Retention and deletion

Local history is bounded by the app's retention limits. The user can clear profile, usage, warnings, breaks, resets, rewards, games, and local crash logs from Settings. Android's **Clear storage** and uninstall actions also remove app-private data, subject to Android backup or device-transfer settings. An exported report is outside the app and must be deleted by the user from its chosen destination.

## Security and limitations

Data is kept inside Android's app sandbox and is not transmitted by Eye Guardian. Local data can still be removed or altered by Android system tools, device restoration, a rooted device, or a user who has access to the device. Therefore a report is a personal wellness summary, not cryptographic evidence or a medical record.

## Children and medical use

Eye Guardian is not presented as a children's service and is not a medical device. Do not rely on it to diagnose or prevent eye disease. Parents and guardians should decide whether the app is appropriate for a child.

## Changes and contact

Material changes will be reflected in this policy and the release notes. Before Play Store publication, replace the privacy contact placeholder and host this policy at a stable, publicly accessible HTTPS URL.

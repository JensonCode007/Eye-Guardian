# Local data integrity notes

Eye Guardian currently stores small, bounded JSON records in Android app-private `SharedPreferences`. This provides persistence across normal launches and updates, while avoiding a server and keeping the app offline.

It does **not** make the records tamper-proof. A user, root tool, restored backup, corrupted storage, or another system-level event can remove or alter local files. Reports therefore describe locally saved observations and must not be presented as legal, medical, or forensic evidence.

The release contract is:

1. Writes are bounded and use stable date/timestamp fields.
2. Read failures fail closed to an empty section rather than inventing values.
3. Report ranges are bounded to the app installation date and the selected dates.
4. Exports are explicit user actions and are not silently uploaded.
5. Clear-data actions remove the profile, history, rewards, and crash diagnostics.

Before a production-grade integrity guarantee is claimed, migrate history to a versioned Room schema and add authenticated record envelopes backed by Android Keystore, with migration tests and corruption recovery. That is intentionally separate from the GitHub test release.

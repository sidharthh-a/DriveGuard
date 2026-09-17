# Play Store data-safety form — filled checklist

Answers below mirror the implementation; verify nothing changed before
submission. Form path: Play Console → App content → Data safety.

## Overview answers

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | n/a (nothing collected) |
| Do you provide a way for users to request that their data is deleted? | n/a (nothing collected off-device; in-app deletion exists for local data) |

**"Collect" definition note (Play):** data processed ephemerally in memory and
never stored/transmitted is *not* collected. Camera frames are processed in
memory for the duration of a monitoring session only, never persisted, never
transmitted (no INTERNET permission). Answer "No" to collection.

## Data types

| Type | Collected | Shared | Processed ephemerally | Purpose |
|---|---|---|---|---|
| Camera / images (video frames) | No | No | **Yes** — real-time eye-state analysis only | App functionality (drowsiness alerting) |
| App interactions (session metrics) | No (stored locally only, on user's device) | No | — | App functionality (history/summary) |

## Local-only storage disclosure (not part of data-safety, but be consistent)

- Session records (timestamp, duration, alert count, interruptions, avg
  analysis ms) — Room DB, deletable in-app.
- Settings — DataStore, deletable by uninstall/clear-data.

## Permissions justification (Play Console → App content → Permissions)

| Permission | Declaration | Justification text |
|---|---|---|
| `CAMERA` | Prompt at runtime | "DriveGuard monitors eye state in real time to alert drowsy drivers. The camera is used only while monitoring is active; frames are processed on-device and never stored or shared." |
| `VIBRATE` | Normal | Alert vibration for drowsiness warnings. |
| `POST_NOTIFICATIONS` | Prompt at runtime (API 33+) | Persistent notification showing that monitoring is active (platform requirement for camera foreground service). |
| `FOREGROUND_SERVICE_CAMERA` | Manifest only | Required (API 34+) to keep camera-based monitoring alive when the app is backgrounded mid-session. |
| `WAKE_LOCK` | Manifest only | Keeps monitoring consistent during an active session. |

## Safety / claims review (policy compliance)

- Title/description avoid medical claims; the listing says "alerts for signs
  of driver drowsiness", never "prevents accidents".
- No mention of accuracy percentages (none established).
- Safety disclaimer present in listing + in-app (onboarding page 4, home card,
  About → Limitations).
- Not presented as a medical device or certified safety system.

## Pre-submission checklist

- [ ] Replace placeholder contact email in `PRIVACY_POLICY.md`; host it on a public URL and link in Play Console **and** the app (About/Privacy page links to in-app copy; add a remote-URL link too).
- [ ] Content rating questionnaire: mention "driver assistance, no user-generated content".
- [ ] Target audience: adults (18+ recommended given driving context).
- [ ] Upload `app-release.aab` signed with the **production** key.
- [ ] Screenshots re-shot at final quality (see `STORE_LISTING.md` plan).
- [ ] Verify data-safety answers above still match (run a release build and audit network state — the app must remain offline-capable).

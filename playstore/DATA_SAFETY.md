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

## Verified against the build (2026-11-22)

- Shipped release manifest requests only `CAMERA`, `VIBRATE`,
  `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA` and
  `WAKE_LOCK`; `INTERNET` and `ACCESS_NETWORK_STATE` (injected by
  `transport-backend-cct` via ML Kit) are removed with `tools:node="remove"`.
  CI enforces this: `scripts/check-release-permissions.sh` fails the build if
  either permission reappears in the merged manifest, the release APK or the
  release AAB that is uploaded to Play.
- Release build is 16 KB-page compatible: `zipalign -c -P 16 -v 4` passes and
  every shipped 64-bit library has `p_align >= 16384` (LiteRT's `x86_64` binary
  does not, so release artifacts ship `arm64-v8a` + `armeabi-v7a`).
- Face detection is fully offline: the bundled ML Kit models are packed in the
  APK (`assets/models_bundled/blazeface.tfl`, `fssd_*`, `contours.tfl`), and the
  only shipped code that calls `ConnectivityManager` is DataTransport telemetry,
  whose call is wrapped by `SafeLoggingExecutor` (permission failure is logged
  and the event dropped, never a crash).
- `android:allowBackup="false"`: no cloud backup or device-transfer copy of the
  local session history/settings, which matches the policy's "uninstalling the
  app removes all of this data".
- `bundletool`/`keytool` shows the current AAB is **unsigned** — it must be
  rebuilt with the production upload key before upload.

## Pre-submission checklist

- [ ] Replace placeholder contact email in `PRIVACY_POLICY.md`; host it on a public URL and link in Play Console **and** the app (About/Privacy page links to in-app copy; add a remote-URL link too).
- [ ] Content rating questionnaire: mention "driver assistance, no user-generated content".
- [ ] Target audience: adults (18+ recommended given driving context).
- [ ] Upload `app-release.aab` signed with the **production** key.
- [ ] Screenshots re-shot at final quality (see `STORE_LISTING.md` plan).
- [ ] Verify data-safety answers above still match (run a release build and audit network state — the app must remain offline-capable).

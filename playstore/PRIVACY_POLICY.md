# DriveGuard — Privacy Policy (draft)

**Last updated:** 2026-09-16

DriveGuard ("the app") is an offline driver-assistance application. This
policy describes exactly what the app does and does not do with data. It is
written to match the implementation; there are no hidden data flows.

## Camera

The app requests the CAMERA permission and uses the front camera only while a
monitoring session is active (started and stoppable by you). Camera frames are
processed in memory, in real time, for the sole purpose of detecting sustained
eye closure. **Frames are never saved, uploaded, or shared.** When monitoring
stops, camera use stops.

## On-device processing

All computation — face detection and eye-state classification — happens on
your device. The shipped app requests no INTERNET or network-state permission
(the network permissions declared by an ML Kit dependency are removed at build
time), so the app is technically incapable of transmitting data anywhere.

## Data we collect

None. The app has:
- no accounts or sign-up,
- no analytics or crash-reporting SDKs,
- no advertising,
- no tracking of any kind.

## Data stored on your device

The app stores, locally only:
- **Session records**: start time, duration, number of alerts, number of
  monitoring interruptions, and average per-frame analysis time.
- **Settings**: sensitivity, sound/vibration toggles, appearance mode,
  onboarding/checklist acknowledgements.

You can delete individual sessions or clear the entire history from the
History screen or Settings → Clear session history. Uninstalling the app
removes all of this data.

## Permissions requested and why

| Permission | Purpose | Required? |
|---|---|---|
| `CAMERA` | Real-time eye-state monitoring | Required for monitoring; app is unusable for its core purpose without it |
| `VIBRATE` | Drowsiness alert vibration | Core alert output |
| `POST_NOTIFICATIONS` (Android 13+) | Persistent "monitoring active" notification | Required by the platform for the foreground service that keeps monitoring alive |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CAMERA` | Platform requirement to run camera monitoring while the app is backgrounded | Required while monitoring |
| `WAKE_LOCK` | Keep monitoring consistent during a session | Required while monitoring |

The app never requests location, contacts, microphone, or storage.

## Children

The app is not directed at children and does not knowingly collect any data
from anyone, including children.

## Changes

If a future version ever adds network capability or new data collection, this
policy and the Play Store data-safety form will be updated before release.

## Contact

Questions about this policy: [your contact email here].

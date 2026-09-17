# Play Store listing drafts

## App title (30 chars max)

```
DriveGuard: Drowsiness Alert
```

## Short description (80 chars max)

```
On-device alerts for signs of driver drowsiness. Private, offline, no account.
```

## Full description

```
DriveGuard helps you stay alert on the road. Using your phone's front camera
and on-device machine learning, it watches for sustained eye closure — one of
the most common visible signs of drowsiness — and alerts you before it grows
into a microsleep.

HOW IT WORKS
• Mount your phone, start monitoring, and keep your face in view.
• DriveGuard analyses eye state in real time, entirely on your phone.
• If your eyes stay closed for about three seconds, you get an escalating
  alert: a bright warning, an alarm tone and vibration.
• Alerts repeat only as needed — never spam — and escalate from "Stay Alert"
  to "Please stop and rest".

PRIVATE BY DESIGN
• Camera frames are processed in memory and never saved, uploaded or shared.
• The app has no internet permission — it cannot send your data anywhere.
• No account, no ads, no analytics, no tracking.

CLEAR FEEDBACK
• "Monitoring" while your eyes are open.
• "Face not detected" guidance if the camera loses sight of you — it is never
  treated as drowsiness.
• Session history: duration, alerts and interruptions, stored only on your
  device and deletable at any time.

ADJUSTABLE
• Sensitivity: how quickly the app reacts.
• Sound and vibration toggles.
• Light / dark / system theme.

IMPORTANT
DriveGuard is an assistance tool, not a safety system. It does not replace
responsible driving, adequate rest or professional driver-monitoring systems.
If you feel tired, stop and rest. Results depend on lighting, camera position
and glasses. It can miss events or raise false alerts.
```

## Feature list (for Play "key features" / graphics)

1. Real-time eye-state monitoring, fully on-device
2. Escalating visual + audio + vibration alerts
3. 3-second continuous-closure rule with anti-false-alarm hysteresis
4. Face-aware guidance ("Face not detected" is not drowsiness)
5. Session history with one-tap deletion
6. Sensitivity, sound and vibration settings
7. Dark mode and system theme support
8. No internet permission — nothing leaves your phone
9. No account, no ads, no tracking
10. TalkBack-accessible UI

## Release notes (v1.0.0)

```
Initial release.
• Real-time drowsiness alerts from front-camera eye monitoring
• On-device processing only — no internet permission
• Escalating alert levels with cooldown
• Session history and summary
• Sensitivity, alert and appearance settings
```

## Screenshot plan (min 2, max 8; 1080×1920 or larger, 16:9/9:16)

1. **Home dashboard** — brand, "Start driving", recent-session card (shows
   polish; capture in light mode).
2. **Pre-drive checklist** — safety framing (builds trust).
3. **Driving, normal** — camera preview with "Monitoring" status band.
4. **Driving, alert** — red "DROWSINESS DETECTED" band (the money shot; stage
   with closed eyes or via the emulated scene).
5. **Session summary** — duration/alerts/interruptions metrics.
6. **History** — list of sessions with delete actions.
7. **Settings** — sensitivity + alert toggles.
8. **Privacy page** — reinforces the privacy story.

Existing captures from the validation emulator are in `tools/0*.png` and can
be re-shot at final quality on a real device.

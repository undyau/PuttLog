# Play Console "Data safety" form answers

Based on what the app actually does (see docs/privacy.html):

- **Does your app collect or share any of the required user data types?** No.
- **Data collected:** None. No analytics SDK, no crash reporting SDK, no ads SDK, no network calls of any kind.
- **Data shared with third parties:** None.
- **Is data encrypted in transit?** N/A — no data leaves the device.
- **Can users request data deletion?** Yes — in-app, via Settings > Data (delete by date range), or by uninstalling the app.
- **Permissions declared and why:**
  - `RECORD_AUDIO` — optional on-device voice input (off by default).
  - `BLUETOOTH` / `BLUETOOTH_CONNECT` — route mic audio through a connected Bluetooth headset during voice input.
  - `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` — required by Android to show the listening notification while voice input is active.
  - `WAKE_LOCK` — keep the recognizer running while the screen is on during a listening session.

# Content rating questionnaire

App has no user-generated content, no violence, no in-app purchases, no ads, no
sharing/social features. Should qualify for the lowest rating tier (PEGI 3 / Everyone).

# Suggested category

Sports (or Health & Fitness) — pick whichever Play Console offers that best matches
"disc golf practice tracking."

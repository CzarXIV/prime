# Prime

Prime your phone for the work. An Android app that turns your digital environment toward focus.

**Download:** https://github.com/CzarXIV/prime/releases/latest/download/prime.apk

## What it does

- **Intention on unlock** — every unlock (or at most every N minutes) shows today's intention and top 3, or a line from your code if you haven't primed the day.
- **Distraction friction** — apps on your distraction list get a pause and a question before they open: *why are you opening this?* Back to work, or wait out the timer and take a short pass.
- **Focus modes**
  - Deep Work — 30s pause, 5 min pass
  - Learning — 15s pause, 10 min pass
  - Rest — guard off
  - Optional: home and lock screen wallpaper switches to your current mode.
- **Rituals + streaks** — morning priming (one intention, three tasks), evening review (wins, lesson, effort rating). A day counts toward the streak when both are done. Daily reminders at times you choose.

## Setup on the phone

1. Install the APK (allow installs from your browser when asked).
2. Open Prime → **GUARD** tab → **Guard service** → switch Prime on under Accessibility.
   - If Android blocks it as a *restricted setting*: App info → ⋮ → **Allow restricted settings**, then try again.
3. Pick your distraction apps on the same tab.

The accessibility service only listens for which app comes to the foreground. It never reads screen content or what you type. All data stays on the device.

## Build

Kotlin + Jetpack Compose, minSdk 26. Every push to `main` builds a signed APK in GitHub Actions and publishes it as the latest release.

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

- **Content guard (TRAIN tab)** — built to train discipline, not replace it. When adult content shows up, Prime catches the moment and hands you the choice: **Walk away** (a rep), **Give me 60 seconds** (a short reset), or **Continue** (a slip, no judgement).
  - Detection: on-screen text vs. an editable watch-word list, plus an optional on-device image check once a minute (Android 11+).
  - Levels adapt: Coach (full pause) → Nudge (small card) → Banner (light banner). 7 clean days earn the next level; 2 slips in 3 days bring more support back.
  - Evening review asks what was going on after a slip; patterns (time, app, place, mood) show after 3 slips.

## Setup on the phone

1. Install the APK (allow installs from your browser when asked).
2. Open Prime → **GUARD** tab → **Guard service** → switch Prime on under Accessibility.
   - If Android blocks it as a *restricted setting*: App info → ⋮ → **Allow restricted settings**, then try again.
3. Pick your distraction apps on the same tab.

With the content guard on, the accessibility service reads on-screen text (never password fields) and, once a minute, takes a screenshot for the image model. Both are checked in memory on the phone and discarded immediately. Nothing is saved or sent anywhere. All data stays on the device.

Image model: `assets/nsfw.tflite` from [nipunru/nsfw-detector-android](https://github.com/nipunru/nsfw-detector-android) (MIT).

## Build

Kotlin + Jetpack Compose, minSdk 26. Every push to `main` builds a signed APK in GitHub Actions and publishes it as the latest release.

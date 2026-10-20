# ChargeView

Native Android (Kotlin + Jetpack Compose) battery monitor. See [FSD.md](FSD.md) for the spec and
[ChargeView-mockups.html](ChargeView-mockups.html) for the design mockups.

## Implemented
- Home: battery %, charging/discharging state, time to target (taper-aware) or time until empty,
  signed battery power, last-minute and 10-minute SoC change, temperature, graph with Y axis and touch inspection.
- History: recorded sessions (paged) and a level graph; recording is stored on the device.
- Home cards: Settings › Home screen cards turns each card on/off (battery charge, power, SoC change, temperature,
  last minute, voltage, mAh rate & total). Hold and drag a card on Home to reorder; the first card is large with
  extra detail. Tapping a card shows its graph in the large graph card. Each small card has a last-10-minute trend line.
- Estimates: time to target / empty uses SoC step timings over 20 min (SoC is whole numbers), falling back to
  5-minute smoothed current, so brief spikes don't swing the result. SoC change is shown from the last
  plug/unplug event, growing minute by minute up to a rolling 10 minutes.
- Settings: Light / Dark / Follow system, charge target, recording on/off, clear history.
- Adapts in place to the Fold7 cover/unfolded screens (no restart).

## Not yet implemented
- Background (foreground-service) recording: readings are recorded only while the app is running.
- Log time axis, diagnostics screen, device-provided history import.

## Build
JDK 17 and the Android SDK (platform 35) are required.

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow in
`.github/workflows/android-debug-apk.yml` builds the same APK.

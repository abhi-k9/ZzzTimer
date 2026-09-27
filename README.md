# ZzzTimer

[![CI](https://github.com/abhi-k9/ZzzTimer/actions/workflows/ci.yml/badge.svg)](https://github.com/abhi-k9/ZzzTimer/actions/workflows/ci.yml)

ZzzTimer helps you fall asleep while listening to music or podcasts.
When the timer ends, the media volume is gradually lowered, playback is paused, then the volume is restored.

It is a from-scratch rewrite of [Sleep Timer](https://github.com/SimonMarquis/SleepTimer) by Simon Marquis (see [NOTICE](NOTICE)).

## Usage

1. Add the ZzzTimer tile to the Quick Settings panel.
2. Tap the tile to start a timer of the default duration, tap it again to stop it.
3. Extend, reduce or stop the timer from its notification. Dismissing the notification (possible since Android 14) also
   stops the timer.
4. Open the app (launcher icon, notification tap, or long press on the tile) to:
   - start a timer of an exact duration, or pick a preset,
   - configure the default duration and the `+` / `−` steps,
   - choose the theme (System, Light, Dark) and Material You dynamic colors,
   - allow or forbid other apps to control the timer.

## Automation

Tools like [Tasker](https://tasker.joaoapps.com/) or `adb` can control the timer with explicit broadcasts.
This can be turned off in the app settings.

| Action                                     | Effect                                                         |
|--------------------------------------------|----------------------------------------------------------------|
| `io.github.abhik9.zzztimer.action.START`     | Starts a timer: of `duration` seconds, or the default duration |
| `io.github.abhik9.zzztimer.action.UPDATE`    | Adds `duration` seconds (can be negative) to the running timer |
| `io.github.abhik9.zzztimer.action.INCREMENT` | Extends the running timer by the configured step               |
| `io.github.abhik9.zzztimer.action.DECREMENT` | Reduces the running timer by the configured step               |
| `io.github.abhik9.zzztimer.action.TOGGLE`    | Starts the default timer, or stops the running one             |
| `io.github.abhik9.zzztimer.action.STOP`      | Stops the running timer                                        |

`duration` is a `long` or `int` extra, in seconds, capped to 24 hours. For example:

```bash
# Start a 10 minutes timer
adb shell am broadcast -n io.github.abhik9.zzztimer/.automation.AutomationReceiver \
  -a io.github.abhik9.zzztimer.action.START --el duration 600

# Remove 1 minute
adb shell am broadcast -n io.github.abhik9.zzztimer/.automation.AutomationReceiver \
  -a io.github.abhik9.zzztimer.action.UPDATE --el duration -60

# Stop
adb shell am broadcast -n io.github.abhik9.zzztimer/.automation.AutomationReceiver \
  -a io.github.abhik9.zzztimer.action.STOP
```

Debug builds use the `io.github.abhik9.zzztimer.debug` package: adjust the component name (`-n`) accordingly.

## How it works

The ongoing notification is the source of truth of the timer: the timer exists only while its notification is posted,
and the system removes the notification when the timer ends
([`setTimeoutAfter`](https://developer.android.com/reference/android/app/Notification.Builder#setTimeoutAfter(long))).
Nothing is persisted, and there is nothing to clean up after a reboot.

The end of the timer is tracked on the monotonic `elapsedRealtime` clock, stored in the notification extras, so changing
the time or the time zone doesn't affect a running timer.

When the timer ends:

- **Up to Android 16**, the notification [`deleteIntent`](https://developer.android.com/reference/android/app/Notification.Builder#setDeleteIntent(android.app.PendingIntent))
  starts the sleep service.
- **Since Android 17**, [background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)
  requires a foreground service to lower the volume, and a `deleteIntent` can't start one. An
  [exact alarm](https://developer.android.com/reference/android/app/AlarmManager#setExactAndAllowWhileIdle(int,%20long,%20android.app.PendingIntent))
  starts a `shortService` foreground service instead, which requires the *Alarms & reminders* permission.

In both cases, a signal received well before the deadline means the user dismissed the notification: the timer is
cancelled instead.

Since Android 16 QPR2, the notification is displayed as a
[Live Update](https://developer.android.com/develop/ui/views/notifications/live-update).

## Project structure

| Module  | Content                                                                                                      |
|---------|--------------------------------------------------------------------------------------------------------------|
| `:core` | Pure Kotlin: the timer logic (`SleepTimer`), the fade-out routine (`SleepRoutine`), settings and time helpers. Unit tested on the JVM. |
| `:app`  | Android adapters (notification, alarm, audio, tile, receivers) and the Jetpack Compose UI.                   |

`:core` defines small interfaces (`TimerDisplay`, `SleepTrigger`, `MediaAudio`, `DeviceClock`) that `:app`
implements with the platform APIs.

## Building

Requirements: JDK 17 or newer, and the Android SDK (API 37).

```bash
./gradlew assembleDebug    # debug APK
./gradlew :core:test       # unit tests
./gradlew lint             # Android lint (warnings are errors)
```

Release builds are minified. They are signed only when a signing configuration is provided, as Gradle properties
(e.g. in `~/.gradle/gradle.properties`) or environment variables — never commit keystores or passwords:

| Gradle property                  | Environment variable                 |
|----------------------------------|--------------------------------------|
| `zzztimer.signing.storeFile`     | `ZZZTIMER_SIGNING_STORE_FILE`        |
| `zzztimer.signing.storePassword` | `ZZZTIMER_SIGNING_STORE_PASSWORD`    |
| `zzztimer.signing.keyAlias`      | `ZZZTIMER_SIGNING_KEY_ALIAS`         |
| `zzztimer.signing.keyPassword`   | `ZZZTIMER_SIGNING_KEY_PASSWORD`      |

## Releasing

Pushing a `vX.Y.Z` tag matching the version in `app/build.gradle.kts` runs the [release workflow](.github/workflows/release.yml):
it builds, tests and lints the app, signs the APK, verifies the signature, attests its build provenance, and publishes
it with its SHA-256 checksum as a GitHub Release.

It requires these repository secrets (Settings → Secrets and variables → Actions):

| Secret                              | Value                                   |
|-------------------------------------|-----------------------------------------|
| `ZZZTIMER_SIGNING_KEYSTORE_BASE64`  | The release keystore, base64 encoded    |
| `ZZZTIMER_SIGNING_STORE_PASSWORD`   | The keystore password                   |
| `ZZZTIMER_SIGNING_KEY_ALIAS`        | The key alias                           |
| `ZZZTIMER_SIGNING_KEY_PASSWORD`     | The key password                        |

A keystore can be created once with `keytool` (keep it and its passwords safe: every future update must be signed with
the same key):

```bash
keytool -genkeypair -keystore zzztimer-release.jks -alias zzztimer -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 zzztimer-release.jks   # value of ZZZTIMER_SIGNING_KEYSTORE_BASE64 (macOS: base64 -i zzztimer-release.jks)
```

Anyone can check that a downloaded APK was built by this repository's workflow:

```bash
gh attestation verify ZzzTimer-vX.Y.Z.apk --repo abhi-k9/ZzzTimer
```

## Privacy

ZzzTimer has no Internet permission and collects no data, see the [privacy policy](PRIVACY.md).

## License

    Copyright 2020 Simon Marquis
    Copyright 2026 abhi-k9

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

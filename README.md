# <img src="fastlane/metadata/android/en-US/images/icon.png" alt="" width="48" align="top"> ZzzTimer

[![CI](https://github.com/abhi-k9/ZzzTimer/actions/workflows/ci.yml/badge.svg)](https://github.com/abhi-k9/ZzzTimer/actions/workflows/ci.yml)

ZzzTimer helps you fall asleep while listening to music or podcasts: when the timer ends, the media volume is gradually
lowered, playback is paused, then the volume is restored.

Download it from the [latest release](https://github.com/abhi-k9/ZzzTimer/releases/latest) (see
[verifying a download](#verifying-a-download)). It is a rewrite of [Sleep Timer](https://github.com/SimonMarquis/SleepTimer)
by Simon Marquis, see [NOTICE](NOTICE).

## Usage

- **Quick Settings tile**: tap to start a timer of the default duration, tap again to stop it.
- **Notification**: extend, reduce or stop the timer. Dismissing it (possible since Android 14) stops the timer.
- **App** (launcher, notification tap, or long press on the tile): start a timer of any duration, configure the default
  duration and the `+` / `−` steps, the fade out, rewinding, the theme, automation and diagnostics.

**Fade out**: how long the volume takes to go down when the timer ends, whatever the volume: 30 seconds by default, up
to a minute, or off to pause right away.

**Rewind to where the fade started** (optional): once paused, playback goes back to where the volume started to fade,
plus 5% of the fade, and at most the fade played at 3× speed. It needs *Notification access*, only to find the playing
media: ZzzTimer never reads notifications. If the APK was installed from a browser or a file manager, Android may first
ask to allow *restricted settings*, from ZzzTimer's App info menu (⋮).

## Automation

Tools like [Tasker](https://tasker.joaoapps.com/) or `adb` can control the timer with explicit broadcasts to
`io.github.abhik9.zzztimer/.automation.AutomationReceiver` (`io.github.abhik9.zzztimer.debug/…` for debug builds).
This can be turned off in the app settings.

| Action (`io.github.abhik9.zzztimer.action.…`) | Effect                                                              |
|-----------------------------------------------|---------------------------------------------------------------------|
| `START`                                       | Starts a timer of `duration` seconds, or of the default duration    |
| `UPDATE`                                      | Adds `duration` seconds (can be negative) to the running timer      |
| `INCREMENT` / `DECREMENT`                     | Extends / reduces the running timer by the configured step          |
| `TOGGLE`                                      | Starts the default timer, or stops the running one                  |
| `STOP`                                        | Stops the running timer                                             |

`duration` is a `long` or `int` extra, capped to 24 hours:

```bash
adb shell am broadcast -n io.github.abhik9.zzztimer/.automation.AutomationReceiver \
  -a io.github.abhik9.zzztimer.action.START --el duration 600
```

## Diagnostics

To investigate an issue, turn on **Record diagnostics** in the app settings, reproduce it, then **Export log** to a file.
The log records what the timer does and why (timer operations, alarms, deadlines, the fade out, the sleep service,
automation, permissions, crashes), and the export starts with a snapshot of the app and device state. It is off by
default, capped at about 512 KB, and never leaves the device unless exported (see the [privacy policy](PRIVACY.md)).

## How it works

- The ongoing notification is the timer's source of truth: the timer exists only while it is posted, and the system
  removes it at the deadline ([`setTimeoutAfter`](https://developer.android.com/reference/android/app/Notification.Builder#setTimeoutAfter(long))).
  Nothing is persisted, so there is nothing to clean up after a reboot.
- The deadline is tracked on the monotonic `elapsedRealtime` clock: changing the time or time zone doesn't affect it.
- When the timer ends, a service fades out and pauses playback (the system may stop it about a minute later, hence the
  maximum fade). Up to Android 16, the notification
  [`deleteIntent`](https://developer.android.com/reference/android/app/Notification.Builder#setDeleteIntent(android.app.PendingIntent))
  starts it. Since Android 17, [background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)
  requires a foreground service, which an exact alarm starts instead (hence the *Alarms & reminders* permission).
- A signal received well before the deadline means the notification was dismissed: the timer is cancelled instead.
- To rewind, the service reads the position of the playing [media session](https://developer.android.com/reference/android/media/session/MediaController)
  when the fade starts and when it ends, then seeks back. Positions are in media time, so the playback speed is
  accounted for. Players that can't seek (e.g. live streams) are only paused. The Notification access listener is only
  there to be enabled: it unbinds itself right away, and never receives notifications.

The `:core` module holds the logic in pure Kotlin, unit tested on the JVM: `SleepTimer`, the fade out (`SleepRoutine`)
and the automation API. It defines small interfaces (`TimerDisplay`, `SleepTrigger`, `MediaAudio`, `PlayingMedia`,
`DeviceClock`, `EventLog`) that `:app` implements with the platform APIs, next to the Jetpack Compose UI.

## Building

Requires JDK 17+ and the Android SDK (API 37). `./gradlew assembleDebug test lint` builds, tests and lints (lint and
Kotlin warnings are errors); CI also runs [ktlint](https://pinterest.github.io/ktlint/) and
[zizmor](https://docs.zizmor.sh/).

Release builds are signed only when a signing configuration is provided. Never commit keystores or passwords:

| Gradle property (`zzztimer.signing.…`) | Environment variable            | Release workflow secret                          |
|----------------------------------------|---------------------------------|--------------------------------------------------|
| `storeFile`                            | `ZZZTIMER_SIGNING_STORE_FILE`   | `ZZZTIMER_SIGNING_KEYSTORE_BASE64` (base64 file) |
| `storePassword`                        | `ZZZTIMER_SIGNING_STORE_PASSWORD` | `ZZZTIMER_SIGNING_STORE_PASSWORD`              |
| `keyAlias`                             | `ZZZTIMER_SIGNING_KEY_ALIAS`    | `ZZZTIMER_SIGNING_KEY_ALIAS`                     |
| `keyPassword`                          | `ZZZTIMER_SIGNING_KEY_PASSWORD` | `ZZZTIMER_SIGNING_KEY_PASSWORD`                  |

## Releasing

Bump the version in `app/build.gradle.kts` on `main`, then run the [release workflow](.github/workflows/release.yml) on
`main` (Actions → Release → Run workflow). It creates the `vX.Y.Z` tag, builds from scratch, tests, lints, signs, checks
the signing certificate, attests the build provenance, and publishes the APK with its checksum. Pushing a tag that points
to `main` works too.

The workflow runs in the `release` environment, which can hold the secrets and protection rules (Settings →
Environments). The keystore is created once, and every update must be signed with it; if it ever changes, update
`SIGNING_CERT_SHA256` in the workflow:

```bash
keytool -genkeypair -keystore zzztimer-release.jks -alias zzztimer -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 zzztimer-release.jks   # macOS: base64 -i zzztimer-release.jks
```

### Verifying a download

- Signing certificate (SHA-256), checked by `apksigner verify --print-certs`, AppVerifier or Obtainium:
  `95:E5:0C:F0:03:27:57:2F:5E:89:C2:25:AF:ED:42:FF:1A:06:3E:F1:2E:3E:ED:07:5F:F3:AB:4B:67:9C:F0:08`
- Build provenance: `gh attestation verify ZzzTimer-vX.Y.Z.apk --repo abhi-k9/ZzzTimer`
- Checksum: `sha256sum --check ZzzTimer-vX.Y.Z.apk.sha256`

## License

[Apache License 2.0](LICENSE). No Internet permission, no data collected: see the [privacy policy](PRIVACY.md).

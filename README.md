# <img src="fastlane/metadata/android/en-US/images/icon.png" alt="" width="48" align="top"> ZzzTimer

[![CI](https://github.com/abhi-k9/ZzzTimer/actions/workflows/ci.yml/badge.svg)](https://github.com/abhi-k9/ZzzTimer/actions/workflows/ci.yml)

ZzzTimer helps you fall asleep while listening to music or podcasts: when the timer ends, the media volume is gradually
lowered, playback is paused, then the volume is restored.

Download it from the [latest release](https://github.com/abhi-k9/ZzzTimer/releases/latest) (see
[verifying a download](#verifying-a-download)). It is a rewrite of [Sleep Timer](https://github.com/SimonMarquis/SleepTimer)
by Simon Marquis, see [NOTICE](NOTICE).

## Usage

- **Quick Settings tile**: tap to start a timer of the default duration, tap again to stop it.
- **Home screen widget**: the same as the tile, and shows until when the timer runs. 1×1 by default, resizable.
- **Notification**: extend, reduce or stop the timer. Dismissing it (possible since Android 14) stops the timer.
- **App** (launcher, notification tap, or long press on the tile): start a timer of any duration, and configure
  everything below.

**Before the timer ends**:
- **Vibrate**: a small vibration warns that the fade starts soon (light by default, up to strong, or off), 1 to 10
  minutes before (1 by default). The **Test** button vibrates right away.
- **Shake to keep listening** (optional): from the warning until the end of the fade, shaking the phone extends the
  timer by the `+` step, or stops the fade: playback goes on at its volume, with a new timer. The shake strength has five
  levels, so that turning over in bed doesn't count; **Test the shake** shows how hard you shake against what is needed.

**When the timer ends**:
- **Fade out**: how long the volume takes to go down, whatever the volume: 30 seconds by default, up to a minute, or off
  to pause right away. The steps get longer as it gets quiet.
- **Test the timer end**: play something and start a 10 second timer (the screen can be locked): ZzzTimer then reports
  whether it could lower the volume, pause and rewind.

**Bedtime** (optional): from bedtime (23:00 by default) and for 3 hours, playing something without a timer starts one of
the default duration.

**Rewind to where the fade started** (optional): once paused, playback goes back to where the volume started to fade,
plus 5% of the fade, and at most the fade played at 3× speed. It needs *Notification access*, only to find the playing
media: ZzzTimer never reads notifications. If the APK was installed from a browser or a file manager, Android may first
ask to allow *restricted settings*, from ZzzTimer's App info menu (⋮). With it, ZzzTimer also pauses the playing app
directly, rather than the app that played last.

If Android restricts ZzzTimer in the background (battery usage *Restricted*, Samsung's *Deep sleeping apps*), the app
warns that the timer may not pause playback. On Samsung phones, also keep ZzzTimer out of *Sleeping apps*.

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
The log records what the timer does and why (timer operations, alarms, deadlines, the warning, shakes, bedtime, the
fade out and its report, the sleep service, automation, permissions, crashes), and the export starts with a snapshot of
the app and device state. It is off by
default, capped at about 512 KB, and never leaves the device unless exported (see the [privacy policy](PRIVACY.md)).

## How it works

- The ongoing notification is the timer's source of truth: the timer exists only while it is posted, and the system
  removes it at the deadline ([`setTimeoutAfter`](https://developer.android.com/reference/android/app/Notification.Builder#setTimeoutAfter(long))).
  Nothing about it is persisted, so there is nothing to clean up after a reboot.
- The deadline is tracked on the monotonic `elapsedRealtime` clock: changing the time or time zone doesn't affect it.
- When the timer ends, a service fades out and pauses playback (the system may stop it about a minute later, hence the
  maximum fade). Up to Android 16, the notification
  [`deleteIntent`](https://developer.android.com/reference/android/app/Notification.Builder#setDeleteIntent(android.app.PendingIntent))
  starts it. Since Android 17, [background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)
  requires a foreground service, which an exact alarm starts instead (hence the *Alarms & reminders* permission). Only a
  service started while you use ZzzTimer (app, tile, widget or notification) may lower the volume, so it is started with
  the timer, and kept running with its notification until it ends. Timers started in the background (automation,
  bedtime) pause without fading, unless extended or reduced from the notification.
- The warning before the end is another alarm, exact when allowed. When shaking is on, it also starts the sleep service,
  which watches the accelerometer in the foreground until the sleep is done.
- Bedtime is an alarm checking about every 5 minutes during the window whether media plays, scheduled again after a
  reboot, an update, or a time or time zone change.
- A signal received well before the deadline means the notification was dismissed: the timer is cancelled instead.
- To rewind, the service reads the position of the playing [media session](https://developer.android.com/reference/android/media/session/MediaController)
  when the fade starts and when it ends, then seeks back. Positions are in media time, so the playback speed is
  accounted for. Players that can't seek (e.g. live streams) are only paused. The Notification access listener is only
  there to be enabled: it unbinds itself right away, and never receives notifications.

The `:core` module holds the logic in pure Kotlin, unit tested on the JVM:

- `SleepTimer`: every timer operation, and the warning before the end.
- `SleepRoutine`: the fade out, pause and rewind, and the `SleepReport` of what it did (see `TimerEndTest`).
- `ServiceLifecycle`: when the sleep service runs, and in which foreground; `ShakeDetector`: what a shake is.
- `Bedtime`, the one-tap controls (`toggleControl`, `WidgetLayout`), the automation API, and the settings bounds.

It defines small interfaces (`TimerDisplay`, `SleepTrigger`, `MediaAudio`, `PlayingMedia`, `DeviceClock`, `EventLog`)
that `:app` implements with the platform APIs, next to the Jetpack Compose UI.

## Building

Requires JDK 17+ and the Android SDK (API 37). `./gradlew assembleDebug test lint` builds, tests and lints (lint and
Kotlin warnings are errors); CI also runs [ktlint](https://pinterest.github.io/ktlint/) and
[zizmor](https://docs.zizmor.sh/).

Signing and publishing a release: see [RELEASING.md](RELEASING.md).

## Verifying a download

- Signing certificate (SHA-256), checked by `apksigner verify --print-certs`, AppVerifier or Obtainium:
  `95:E5:0C:F0:03:27:57:2F:5E:89:C2:25:AF:ED:42:FF:1A:06:3E:F1:2E:3E:ED:07:5F:F3:AB:4B:67:9C:F0:08`
- Build provenance: `gh attestation verify ZzzTimer-vX.Y.Z.apk --repo abhi-k9/ZzzTimer`
- Checksum: `sha256sum --check ZzzTimer-vX.Y.Z.apk.sha256`

## License

[Apache License 2.0](LICENSE). No Internet permission, no data collected: see the [privacy policy](PRIVACY.md).

# Privacy policy

ZzzTimer is an open source app, provided free of charge and as is.

**ZzzTimer does not collect, store remotely, or share any personal data.**

- The app has no Internet permission, and contains no analytics, advertising, or crash reporting library.
- Your settings, and the technical diagnostics log when you turn it on, are only stored on your device and excluded
  from backups. The log only leaves your device if you export it yourself. Uninstalling the app deletes both.
- When *Shake to keep listening* is on, the accelerometer is read near the end of a timer, and while you test the
  shake, only to detect a shake. Its readings are never stored.
- Rewinding reads the position of the playing media. The diagnostics log (when on) records the playing app and an opaque
  key of the media, never its title.

Permissions the app requests, and why:

| Permission                          | Purpose                                                                                                                  |
|-------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| Notifications                       | Show the running timer and its actions.                                                                                  |
| Promoted notifications              | Show the running timer as a Live Update (Android 16 QPR2 and later).                                                     |
| Alarms & reminders                  | Pause playback exactly when the timer ends (required on Android 17 and later), warn on time before it ends, and bedtime. |
| Foreground service (media playback) | Lower the volume and pause playback when the timer ends, and detect shakes near its end.                                 |
| Vibration                           | The warning that the timer ends soon.                                                                                    |
| Run at startup                      | Schedule bedtime again after a reboot.                                                                                   |
| Notification access (optional)      | Find the playing media when the timer ends, to pause and rewind it. Notifications are never read.                        |

Questions or concerns can be raised on the project's [issue tracker](https://github.com/abhi-k9/ZzzTimer/issues).

This policy is effective as of 2026-10-24.

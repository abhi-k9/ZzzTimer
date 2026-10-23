# Releasing

## Signing

Release builds are signed only when a signing configuration is provided. Never commit keystores or passwords:

| Gradle property (`zzztimer.signing.…`) | Environment variable            | Release workflow secret                          |
|----------------------------------------|---------------------------------|--------------------------------------------------|
| `storeFile`                            | `ZZZTIMER_SIGNING_STORE_FILE`   | `ZZZTIMER_SIGNING_KEYSTORE_BASE64` (base64 file) |
| `storePassword`                        | `ZZZTIMER_SIGNING_STORE_PASSWORD` | `ZZZTIMER_SIGNING_STORE_PASSWORD`              |
| `keyAlias`                             | `ZZZTIMER_SIGNING_KEY_ALIAS`    | `ZZZTIMER_SIGNING_KEY_ALIAS`                     |
| `keyPassword`                          | `ZZZTIMER_SIGNING_KEY_PASSWORD` | `ZZZTIMER_SIGNING_KEY_PASSWORD`                  |

The keystore is created once, and every update must be signed with it; if it ever changes, update `SIGNING_CERT_SHA256`
in the workflow:

```bash
keytool -genkeypair -keystore zzztimer-release.jks -alias zzztimer -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 zzztimer-release.jks   # macOS: base64 -i zzztimer-release.jks
```

## Publishing a release

Bump the version in `app/build.gradle.kts` on `main`, and write its changelog in
`fastlane/metadata/android/en-US/changelogs/<version code>.txt`: the release notes, also for app stores. Then run the
[release workflow](.github/workflows/release.yml) on `main` (Actions → Release → Run workflow). It creates the `vX.Y.Z`
tag, builds from scratch, tests, lints, signs, checks the signing certificate, attests the build provenance, and publishes
the APK with its checksum, and the changelog as release notes. Pushing a tag that points to `main` works too.

The workflow runs in the `release` environment, which can hold the secrets and protection rules (Settings →
Environments).

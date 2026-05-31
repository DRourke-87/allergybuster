# Releasing AllergyBuster to Google Play

This is the production-release runbook. CI handles the build and signing; you
upload the resulting AAB to the Play Console manually (no Play API service
account is wired up yet — keeping the trust surface small for v1).

## One-time setup

### 1. Generate the upload keystore

Run **once**, on a machine you control. Keep the resulting `upload.jks` safe;
if you lose it you cannot publish app updates under the same upload key
(Play App Signing will let you reset it via Google, but it is annoying).

```sh
keytool -genkey -v \
  -keystore upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias allergybuster
```

Pick a strong store password and key password (they can be the same).

### 2. Add GitHub Actions secrets

In **GitHub → repo → Settings → Secrets and variables → Actions → New
repository secret**, add:

| Secret name | Value |
|---|---|
| `ALLERGYBUSTER_KEYSTORE_BASE64` | `base64 -w0 upload.jks` (the whole keystore as one base64 line) |
| `ALLERGYBUSTER_KEYSTORE_PASSWORD` | store password from keytool |
| `ALLERGYBUSTER_KEY_ALIAS` | `allergybuster` |
| `ALLERGYBUSTER_KEY_PASSWORD` | key password from keytool |

### 3. Enrol in Play App Signing

When you create the Play Console listing, accept **Play App Signing**. Google
will keep the production signing key; your upload key (above) only signs
**uploads**. This is the modern default and lets you rotate the upload key if
it's ever compromised.

## Cutting a release

```sh
# 1. Bump versionName AND increment versionCode (by 1) in app/build.gradle.kts.
# 2. Make sure main is green.
# 3. Tag and push.
git tag v1.0.0
git push origin v1.0.0
```

The `Release AAB` workflow fires on the tag. When it finishes (~5 min):

1. Open the workflow run on GitHub Actions.
2. Download the **`allergybuster-release-aab`** artifact → contains
   `app-release.aab`.
3. Also download the **`allergybuster-mapping`** artifact and keep it forever
   — it's needed to deobfuscate any future crash reports.
4. Play Console → Production (or Internal testing for the first run) →
   Create new release → upload the AAB.

## Local signed builds (optional)

If you want to build a signed release on your dev machine instead of via CI:

1. Create `keystore.properties` at the repo root (it's gitignored):

   ```properties
   ALLERGYBUSTER_KEYSTORE_PATH=/absolute/path/to/upload.jks
   ALLERGYBUSTER_KEYSTORE_PASSWORD=...
   ALLERGYBUSTER_KEY_ALIAS=allergybuster
   ALLERGYBUSTER_KEY_PASSWORD=...
   ```

2. Build:

   ```sh
   ./gradlew :app:bundleRelease
   ```

3. Output: `app/build/outputs/bundle/release/app-release.aab`.

If `keystore.properties` is absent and the env vars are unset, the release
build still runs but is **unsigned** — useful for local R8 sanity, not for
uploading.

## Versioning

- `versionCode`: **manual** — increment by 1 in `app/build.gradle.kts` for every
  release. It is not set by CI. It must increase monotonically because
  `AppUpgradeManager` reads it via `BuildConfig.VERSION_CODE` to detect upgrades
  and run data migrations, so each shipped build needs a higher, meaningful code.
- `versionName`: human-readable, edit in `app/build.gradle.kts` before tagging
  (semver: `1.0.0`, `1.0.1`, …).
- Git tag: must match `v*.*.*` to trigger the release workflow.

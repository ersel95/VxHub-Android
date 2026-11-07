# VxHub for Android — Agent Guide

The Android port of [VxHub for iOS](../VxHub%20-%20iOS). Behaviour follows iOS unless a section below
says why it does not. The backend is `volvoxhub` (NestJS); Android talks to the same endpoints.

| Task | Read |
|---|---|
| Integrating VxHub into an app | [INTEGRATION.md](INTEGRATION.md) + [`sample/`](sample) |
| Developing VxHub for Android | This file |

## Layout

```
build-logic/          convention plugins: vxhub.android.library / .compose / vxhub.publish
vxhub-core/           public API (com.vxhub.VxHub), network, storage, purchases, auth, analytics
vxhub-ui/             Compose screens: paywall/ promo/ store/ support/ auth/ banner/ web/;
                      internal/ holds what they share (VxMarkup, screen helpers, strings)
vxhub-<vendor>/       one satellite per third-party SDK, registered through AndroidX Startup
vxhub/                umbrella = core + ui + revenuecat + firebase (iOS's VxHub product)
vxhub-bom/            version alignment
sample/               the reference integration (not published)
```

Core never links a vendor SDK. Satellites implement the interfaces in
`com.vxhub.providers` and register into `VxProviderRegistry`; `VxHubEngine` hands them the
panel's keys after device registration (iOS's `setFirstLaunch`).

## Commands

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew build                                   # compile + unit tests + lint, every module
./gradlew :vxhub-core:testDebugUnitTest           # core tests
./gradlew :sample:installDebug -Pvxhub.sample.hubId=<id> -Pvxhub.sample.environment=PROD
./gradlew publishToMavenLocal                     # what JitPack runs
```

Add `--no-watch-fs` while git worktrees live under `.claude/worktrees/`: Gradle's file watcher
otherwise stalls on their build output.

## Toolchain

Latest stable everything (AGP 9.5, Gradle 9.9, Kotlin 2.4, compileSdk 37.2, Compose BOM
2026.09) because VxHub targets new projects. AGP 9 compiles Kotlin itself — never apply
`org.jetbrains.kotlin.android`. Explicit API mode and `allWarningsAsErrors` are on for every
library module: a deprecation is a build failure, by design.

## Behaviours that MUST be preserved

- **The backend decides premium.** A purchase is done only when `device/after-purchase` verified
  it; a restore only when `device/restore` did. Never set `isPremium` from the store alone.
- **A charged purchase is never lost.** It is written to `PendingPurchaseStore` before the first
  network call and re-sent on every start, after every restore and on demand; caps: 25 attempts,
  30 days. `VerificationOutcome.classify` must keep matching iOS's `VxVerificationOutcome`
  (`VerificationOutcomeTest` / `PendingPurchaseStoreTest` mirror the iOS tests).
- **One refresh at a time.** Concurrent 401s share one `app-auth/refresh` (rotating refresh tokens
  make a second one look stolen and revoke every session). An offline refresh keeps the session.
- **Wire format.** `device_platform` is `"android"` (lower case — the backend compares it that
  way); headers `X-Hub-Id`, `X-Hub-Device-Id`, `X-Hub-Vid` (UUID only), `X-Language-Code`,
  `X-Platform`. POSTs without a body send `{}`. Two retries, one second apart, for transport errors
  only; none for purchase verification, which has its own queue.
- **mTLS.** `assets/vxhub/mtls.p12` is iOS's `vx_mtls_certificate.p12` re-encrypted with 3DES
  (Android cannot decrypt the original RC2-40). Same key, same password. Replace both when the
  certificate rotates (it expires in December 2039).
- **No-backup storage.** Everything VxHub persists lives under `noBackupFilesDir/vxhub`, so a
  backup restored onto another phone never clones a device id, session or pending purchase.
- **Satellites stay optional.** Core must build and run with none linked; every provider call is
  guarded and one vendor's failure must not stop initialization.
- **Same localization keys as iOS.** `BundledStrings` is generated from iOS's
  `Localizable.strings`; Android-only keys sit at its end.

## Releasing

1. Set `vxhub.version` in `gradle.properties` and add the version to `CHANGELOG.md`.
2. `./gradlew build publishToMavenLocal` passes; commit.
3. `git tag X.Y.Z && git push origin main X.Y.Z` (plain semver, no `v`).
4. `gh release create X.Y.Z --title "X.Y.Z" --notes-file <the CHANGELOG section>`.
5. Open `https://jitpack.io/com/github/ersel95/VxHub-Android/X.Y.Z/build.log`: it starts the
   build; artifacts are `com.github.ersel95.VxHub-Android:<module>:X.Y.Z`.

## Known follow-ups

- The iOS SDK's stage host (`stage.api.volvoxhub.com`, also the backend's own stage URL) has no
  DNS record; `VxEnvironment.STAGE` points at it for parity.
- Screens are verified to open on an Android 17 emulator; flows with real products, accounts and
  tickets still need a pass against a configured Android app in the panel.
- Sign in with Apple has no Android provider.

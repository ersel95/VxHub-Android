# VxHub for Android

Server-driven monetization and engagement SDK — the Android port of
[VxHub for iOS](https://github.com/ersel95/VxHub-iOS). The app supplies one value, its **hub id**;
the VxHub panel supplies every third-party key (RevenueCat, Amplitude, AppsFlyer, OneSignal,
Firebase, Sentry, Facebook, Google), the remote config, the products and the translations.

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        VxHub.initialize(this, VxHubConfig(hubId = "YOUR-HUB-ID"))
    }
}
```

That is the whole integration. There is no warm start to wire up (iOS apps call
`VxHub.shared.start()` from `applicationDidBecomeActive`; Android re-registers on every return to
the foreground by itself).

## Requirements

| | |
|---|---|
| minSdk | 24 (Android 7.0) |
| compileSdk | 37 (minor 2) |
| Kotlin | 2.4+ (consumers on Kotlin 2.3 can read the metadata) |
| AGP / Gradle | 9.x / 9.x, JDK 17+ |

## Installation

Artifacts are published through JitPack from git tags.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// app/build.gradle.kts
dependencies {
    implementation(platform("com.github.ersel95.VxHub-Android:vxhub-bom:<tag>"))
    implementation("com.github.ersel95.VxHub-Android:vxhub")            // core + screens + RevenueCat + Firebase
    implementation("com.github.ersel95.VxHub-Android:vxhub-amplitude")  // add only the services you use
    implementation("com.github.ersel95.VxHub-Android:vxhub-appsflyer")
}
```

## Modules

Each satellite wraps exactly one vendor SDK, registers itself at app start, and is configured by
core with the keys the panel returns — linking the artifact is all an app does.

| Artifact | What it adds | iOS counterpart |
|---|---|---|
| `vxhub-core` | Device registration (mTLS), remote config, localization, purchases contract with backend verification, coins, promo codes, QR login, support API, session analytics, accounts | `VxHubCore` |
| `vxhub-ui` | Compose screens: paywalls V1–V4, promo offer V1–V2, coin store V1–V2, support chat, sign-in, banners, in-app web pages | UI in `VxHubCore`, `VxHubBanner` |
| `vxhub-revenuecat` | Google Play purchases through RevenueCat | `VxHubRevenueCat` |
| `vxhub-firebase` | Firebase Analytics from the panel's google-services.json | `VxHubFirebase` |
| `vxhub-amplitude` | Amplitude analytics + Experiment | `VxHubAmplitude` |
| `vxhub-appsflyer` | AppsFlyer attribution | `VxHubAppsFlyer` |
| `vxhub-onesignal` | OneSignal push | `VxHubOneSignal` |
| `vxhub-sentry` | Sentry crash reporting | `VxHubSentry` |
| `vxhub-facebook` | Facebook App Events | `VxHubFacebook` |
| `vxhub-google-signin` | Google sign-in (Credential Manager) | `VxHubGoogleSignIn` |
| `vxhub-media` | Coil images, Lottie, video, audio playback and recording | `VxHubMedia`, `VxMP3Manager` |
| `vxhub` | `core + ui + revenuecat + firebase` — the iOS package's `VxHub` product | `VxHub` |
| `vxhub-bom` | Version alignment for all of the above | — |

## Using it

State is exposed as `StateFlow`s and every operation is a main-safe `suspend` function.

```kotlin
val isPremium by VxHub.isPremium.collectAsStateWithLifecycle()
val products by VxHub.products.collectAsStateWithLifecycle()

scope.launch {
    when (val result = VxHub.purchase(activity, products.first())) {
        is VxPurchaseResult.Verified -> unlock(result.isPremium)       // the backend's answer
        VxPurchaseResult.PendingVerification -> showPending()          // charged; re-sent until verified
        VxPurchaseResult.PaymentPending -> showPaymentPending()        // cash/bank transfer on Play
        VxPurchaseResult.Cancelled -> Unit
        is VxPurchaseResult.Failed -> showError(result.message)
    }
}

// Ready-made screens, from any Activity or as composables
VxHubUI.showPaywall(activity, VxMainPaywallV4Configuration(), onResult = { … })
VxHubUI.showSupport(activity)
VxHubUI.showAuth(activity) { result -> … }
```

See [INTEGRATION.md](INTEGRATION.md) for the full guide (panel setup, every feature, iOS → Android
API map) and [`sample/`](sample) for a working app.

## Development

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew build                 # compile, unit tests and lint for every module
./gradlew :sample:installDebug -Pvxhub.sample.hubId=<hub id>
```

Read [AGENTS.md](AGENTS.md) before changing behaviour: it lists what must stay in step with iOS.

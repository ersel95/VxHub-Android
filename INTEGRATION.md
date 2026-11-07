# Integrating VxHub into an Android app

## 1. Set the app up in the VxHub panel

Create the **Android** app in the project (accounts, purchases and support are shared with the
project's iOS app). Its id is the `hubId`. Then fill in:

| Panel field | Android value |
|---|---|
| RevenueCat API key | The **Google Play** public SDK key (`goog_…`). Backend verification uses the project's RevenueCat secret key, as for iOS. |
| Firebase config (`firebase_plist_url`) | Upload **google-services.json**. It must contain a client for the app's package name — VxHub never borrows another app's entry. |
| Google client id | A **Web** OAuth client id. Create an *Android* OAuth client (package name + SHA-1 of every signing key, including Play App Signing) in the same Google Cloud project; it only has to exist. |
| App store id | The package name. |
| Store version + force update | The minimum version name. VxHub asks for the update only when Google Play reports it as available, so a version still in review never locks anyone out. |
| AppsFlyer | Only the dev key is needed on Android. |
| OneSignal, Amplitude (+ deployment key), Facebook app id + client token, Sentry DSN | Same as iOS. |

Products: add the Play products to the panel's product list (by product id or `productId:basePlanId`)
and to the RevenueCat **current offering** — VxHub offers what appears in both.

## 2. Add the dependencies

See the [README](README.md#installation). Add satellites (`vxhub-amplitude`, `vxhub-appsflyer`,
`vxhub-onesignal`, `vxhub-sentry`, `vxhub-facebook`, `vxhub-google-signin`, `vxhub-media`) only for
the services the panel configures; each one is picked up automatically at startup.

Nothing else is needed: manifest entries, permissions (`INTERNET`, `ACCESS_NETWORK_STATE`) and R8
rules come with the artifacts. Do **not** apply the google-services Gradle plugin — VxHub configures
Firebase from the panel. (If the app does configure Firebase itself, VxHub uses that instead.)

## 3. Initialize

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        VxHub.initialize(
            application = this,
            config = VxHubConfig(
                hubId = "…",
                environment = VxEnvironment.PROD,
                logLevel = if (BuildConfig.DEBUG) VxLogLevel.DEBUG else VxLogLevel.WARNING,
                nonConsumableProductIds = setOf("lifetime_unlock"),   // one-time products kept forever
            ),
            listener = object : VxHubListener {
                override fun onInitialized() { /* products, config and translations are loaded */ }
                override fun onFailed(error: VxHubException) { /* retried automatically when online */ }
                override fun onBanned() { /* show the ban screen */ }
                override fun onForceUpdateRequired() { VxHub.openStorePage(this@App) }
                override fun onUserSessionExpired() { /* show sign-in */ }
            },
        )
    }
}
```

Or wait for it from a coroutine: `when (VxHub.awaitInitialization()) { SUCCESS, BANNED, FORCE_UPDATE_REQUIRED -> … }`
(throws the `VxHubException` it failed with).

## 4. Everyday API

```kotlin
VxHub.state            // StateFlow<VxHubState>: Initializing, Ready, Failed, Banned, ForceUpdateRequired
VxHub.isPremium        // StateFlow<Boolean> — the backend's answer
VxHub.balance          // StateFlow<Int> — VxHub coins
VxHub.products         // StateFlow<List<VxProduct>>
VxHub.remoteConfig     // StateFlow<VxRemoteConfig>: getString/getBoolean/getInt/getDouble/getJson
VxHub.localize("Key", "English default")
```

### Purchases

```kotlin
val result = VxHub.purchase(activity, product)        // VxPurchaseResult
val restore = VxHub.restorePurchases()                // VxRestoreResult
```

A charged purchase is written to a persistent queue **before** the backend is asked to verify it,
and re-sent on every start until verified (or 25 attempts / 30 days). So `PendingVerification`
means "paid, premium arrives shortly", never "failed". `PaymentPending` is Google Play's
cash/bank-transfer state.

### Ready-made screens

```kotlin
// Each launcher sits next to its screen: import com.vxhub.ui.paywall.showPaywall,
// com.vxhub.ui.support.showSupport, … (the IDE offers them).
VxHubUI.showPaywall(activity, VxMainPaywallV4Configuration(), onResult = { result -> … })
VxHubUI.showPromoOffer(activity, productIdentifier = "pro_yearly_offer", productToCompareIdentifier = "pro_yearly") { purchased -> }
VxHubUI.showStore(activity, VxStoreV2Configuration()) { purchased, productId -> }
VxHubUI.showSupport(activity)
VxHubUI.showAuth(activity) { result -> if (result is VxAuthResult.SignedIn) … }
VxHub.showBanner("Saved", VxBannerType.SUCCESS)
VxHub.showEula(context); VxHub.showPrivacy(context)
```

In Compose, embed the same screens: `VxPaywall(…)`, `VxPromoOffer(…)`, `VxStore(…)`, `VxSupport(…)`,
`VxAuth(…)`.

### Accounts

```kotlin
VxHub.authConfig       // which methods the panel enabled
VxHub.currentUser      // StateFlow<VxUser?>
VxHub.signIn(email, password); VxHub.signUp(email, password, name)
VxHub.forgotPassword(email); VxHub.resetPassword(email, code, newPassword)
VxHub.verifyEmail(code); VxHub.changePassword(current, new)
VxHub.signInWithGoogle(activity)   // needs vxhub-google-signin
VxHub.signOut(allDevices = false)
```

Failures throw `VxHubException.AuthFailed(code, statusCode)` with the API's code
(`INVALID_CREDENTIALS`, `EMAIL_ALREADY_REGISTERED`, `PASSWORD_TOO_SHORT:10`, …). Tokens are stored
encrypted with an Android Keystore key, refreshed silently (once, even under concurrent 401s), and
a rejected session calls `onUserSessionExpired()`.

### Everything else

| Feature | Call |
|---|---|
| Session analytics | `VxHub.trackScreen("Home")`, `VxHub.trackEvent("paywall_shown", mapOf(…))` |
| Amplitude / AppsFlyer | `VxHub.logAmplitudeEvent(…)`, `VxHub.logAppsFlyerEvent(…)`, `VxHub.getVariantPayload("main_paywall")` |
| Coins | `VxHub.spendCoins(1, reason = "export")` → `Spent / Premium / Insufficient / Failed` |
| Promo codes | `VxHub.usePromoCode(code)` → `Redeemed / Invalid` |
| Retention gift | `VxHub.claimRetentionCoinGift()`, `markRetentionCoinAsGiven()`, `hasGivenRetentionCoin()` |
| QR login | `VxHub.validateQrCode(token)` |
| Delete device | `VxHub.deleteAccount()` |
| Language | `VxHub.changePreferredLanguage("tr")`, `VxHub.supportedLanguages` |
| Review | `VxHub.requestReview(activity)` (in-app review every 30 days, store page between) |
| Media | `VxHub.downloadImage/downloadVideo`, `VxAsyncImage`, `VxLottie`, `VxVideoPlayer`, `VxAudioPlayer` (`vxhub-media`) |

## iOS → Android

| iOS | Android |
|---|---|
| `VxHub.shared.initialize(config:delegate:…)` | `VxHub.initialize(application, config, listener)` |
| `VxHub.shared.start()` in `applicationDidBecomeActive` | automatic (`VxHubConfig.autoStartOnForeground`); `VxHub.start()` on demand |
| `VxHubDelegate` | `VxHubListener` (all methods optional) |
| `VxHubObserver` / `vxHubStateDidChange` | the `StateFlow`s on `VxHub` |
| completion handlers / `async` | `suspend` functions |
| `revenueCatProducts: [VxStoreProduct]` | `products: StateFlow<List<VxProduct>>` |
| `purchase(_:resultCompletion:)` | `purchase(activity, product): VxPurchaseResult` |
| `requestAtt` / IDFA | `collectAdvertisingId` / Google Advertising ID (needs `play-services-ads-identifier`) |
| `setAPNsToken` | not needed — OneSignal manages FCM |
| `signInWithApple` | not available on Android; `VxAuthConfig.isAppleEnabled` is informational |
| `showMainPaywall` / `showPaywallV3` / `showPaywallV4` | `VxHubUI.showPaywall(context, configuration)` — the configuration type picks the layout |
| `showContactUs(from:)` | `VxHubUI.showSupport(context)` |
| `VxFont.custom("Manrope")` | `VxFont.Custom(FontFamily(…))` |

## Deliberate differences from iOS

- **Device id:** `ANDROID_ID` (stable across reinstalls for the same signing key, different per
  device), falling back to a random id. SDK state lives in `noBackupFilesDir`, so a backup restored
  on another phone can never carry this device's id, session or pending purchases with it.
- **Force update** also requires the installed version to be older than the panel's version.
- **Initialization** that failed for want of a network is retried on the next foreground or
  reconnect, instead of waiting for a cold start.
- **Translations** are re-fetched on every start with HTTP caching, so a panel edit reaches apps
  without a URL change.
- **`signOut(allDevices = true)`** calls `app-auth/logout-all`; an offline token refresh keeps the
  session instead of signing the person out.
- **Last-known state** (premium, balance, remote config) is restored from disk before the network
  answers.

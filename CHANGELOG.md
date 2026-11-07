# Changelog

## 0.1.0 — 2026-11-07

First release of VxHub for Android, feature-matched with VxHub for iOS
(`feature/app-user-auth` + `feat/spend-coins`).

- Core: device registration over mutual TLS, server-driven config, remote config, localization,
  connectivity, process lifecycle (automatic warm start), session analytics, coins, promo codes,
  retention gift, QR login, device deletion, in-app review, force update.
- Purchases: RevenueCat products joined with the panel's list, backend-verified purchases with a
  persistent pending queue, backend-verified restore.
- Accounts: email/password, Google, email verification, password reset, profile, sessions.
- Screens (Compose): paywalls V1–V4, promo offer V1–V2, coin store V1–V2, support chat, sign-in,
  banners, in-app web pages.
- Satellites: RevenueCat, Firebase, Amplitude (+ Experiment), AppsFlyer, OneSignal, Sentry,
  Facebook, Google sign-in, media (Coil, Lottie, Media3, audio recording).
- Apps on Kotlin 2.2+ (AGP 9's built-in Kotlin) can use every module.

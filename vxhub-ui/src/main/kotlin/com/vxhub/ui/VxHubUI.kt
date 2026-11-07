package com.vxhub.ui

/**
 * Entry point for VxHub's ready-made screens when the app is not in Compose (or wants VxHub to
 * own the presentation). Each feature adds its launcher as an extension from its own package:
 *
 * | Launcher | Import |
 * |---|---|
 * | `VxHubUI.showPaywall(…)` | `com.vxhub.ui.paywall.showPaywall` |
 * | `VxHubUI.showPromoOffer(…)` | `com.vxhub.ui.promo.showPromoOffer` |
 * | `VxHubUI.showStore(…)` | `com.vxhub.ui.store.showStore` |
 * | `VxHubUI.showSupport(…)` | `com.vxhub.ui.support.showSupport` |
 * | `VxHubUI.showAuth(…)` | `com.vxhub.ui.auth.showAuth` |
 *
 * Compose apps can embed the same screens directly — `VxPaywall(…)`, `VxPromoOffer(…)`,
 * `VxStore(…)`, `VxSupport(…)`, `VxAuth(…)` are plain composables.
 */
public object VxHubUI

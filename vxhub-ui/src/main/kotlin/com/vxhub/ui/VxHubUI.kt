package com.vxhub.ui

/**
 * Entry point for VxHub's ready-made screens when the app is not in Compose (or wants VxHub to
 * own the presentation). Each feature adds its launcher as an extension in this package:
 * `VxHubUI.showPaywall(…)`, `VxHubUI.showPromoOffer(…)`, `VxHubUI.showStore(…)`,
 * `VxHubUI.showSupport(…)`, `VxHubUI.showAuth(…)`.
 *
 * Compose apps can embed the same screens directly: `VxPaywall(…)`, `VxSupport(…)`, … — they are
 * plain composables.
 */
public object VxHubUI

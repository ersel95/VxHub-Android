plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub for Android — core, ready-made screens, RevenueCat and Firebase: the iOS package's VxHub product."

android {
    namespace = "com.vxhub.bundle"
}

// The same set as the iOS package's `VxHub` product (core + RevenueCat + Firebase), plus the
// Compose screens. Every other satellite is opt-in: add `vxhub-amplitude`, `vxhub-appsflyer`, … next
// to this artifact, and VxHub picks it up at runtime.
dependencies {
    api(project(":vxhub-core"))
    api(project(":vxhub-ui"))
    api(project(":vxhub-revenuecat"))
    api(project(":vxhub-firebase"))
}

plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub purchases through RevenueCat (Google Play Billing)."

android {
    namespace = "com.vxhub.revenuecat"
}

dependencies {
    api(project(":vxhub-core"))
    api(libs.revenuecat)
    implementation(libs.androidx.startup)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

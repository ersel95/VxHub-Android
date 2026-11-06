plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub crash reporting through Sentry."

android {
    namespace = "com.vxhub.sentry"
}

dependencies {
    api(project(":vxhub-core"))
    api(libs.sentry.android)
    implementation(libs.androidx.startup)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

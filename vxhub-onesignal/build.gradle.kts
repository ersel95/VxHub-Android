plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub push notifications through OneSignal."

android {
    namespace = "com.vxhub.onesignal"
}

dependencies {
    api(project(":vxhub-core"))
    api(libs.onesignal)
    implementation(libs.androidx.startup)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub analytics and experiments through Amplitude."

android {
    namespace = "com.vxhub.amplitude"
}

dependencies {
    api(project(":vxhub-core"))
    api(libs.amplitude.analytics)
    api(libs.amplitude.experiment)
    implementation(libs.androidx.startup)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

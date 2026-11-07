plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.vxhub.compose)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub media: cached images (Coil), Lottie animations, video and audio playback, audio recording."

android {
    namespace = "com.vxhub.media"
}

dependencies {
    api(project(":vxhub-core"))
    api(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.okhttp)
    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    api(libs.lottie.compose)
    api(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

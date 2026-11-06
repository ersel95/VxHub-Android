plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.vxhub.compose)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub ready-made screens in Jetpack Compose: paywalls, promo offers, store, support, sign-in, banners."

android {
    namespace = "com.vxhub.ui"
}

dependencies {
    api(project(":vxhub-core"))

    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.startup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}

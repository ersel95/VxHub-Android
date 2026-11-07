plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub Firebase: configured at runtime from the panel's google-services.json."

android {
    namespace = "com.vxhub.firebase"
}

dependencies {
    api(project(":vxhub-core"))
    // Not api: Firebase is VxHub's to configure, and keeping it off the app's compile classpath keeps
    // its Kotlin 2.4 metadata away from apps on older compilers.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.startup)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

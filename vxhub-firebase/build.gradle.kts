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
    api(platform(libs.firebase.bom))
    api(libs.firebase.analytics)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.startup)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

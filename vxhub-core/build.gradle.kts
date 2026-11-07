plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub core: device registration, server-driven config, purchases contract, auth, support, analytics."

android {
    namespace = "com.vxhub.core"
}

dependencies {
    api(libs.coroutines.core)
    // @RestrictTo on the SDK-internal entry points that satellite modules share.
    api(libs.androidx.annotation)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.browser)
    implementation(libs.play.review)
    implementation(libs.play.app.update)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

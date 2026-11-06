plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub Facebook App Events."

android {
    namespace = "com.vxhub.facebook"
}

dependencies {
    api(project(":vxhub-core"))
    api(libs.facebook.core)
    implementation(libs.androidx.startup)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

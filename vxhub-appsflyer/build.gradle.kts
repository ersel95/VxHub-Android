plugins {
    alias(libs.plugins.vxhub.library)
    alias(libs.plugins.vxhub.publish)
}

description = "VxHub install attribution through AppsFlyer."

android {
    namespace = "com.vxhub.appsflyer"
}

dependencies {
    api(project(":vxhub-core"))
    api(libs.appsflyer)
    implementation(libs.androidx.startup)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

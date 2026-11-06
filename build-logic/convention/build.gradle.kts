plugins {
    `kotlin-dsl`
}

group = "com.vxhub.buildlogic"

dependencies {
    // compileOnly: the root build puts the real plugins on the classpath (see ../../build.gradle.kts),
    // so convention plugins and modules always agree on one AGP/KGP version.
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("vxhubAndroidLibrary") {
            id = "vxhub.android.library"
            implementationClass = "VxAndroidLibraryPlugin"
        }
        register("vxhubAndroidCompose") {
            id = "vxhub.android.compose"
            implementationClass = "VxAndroidComposePlugin"
        }
        register("vxhubPublish") {
            id = "vxhub.publish"
            implementationClass = "VxPublishPlugin"
        }
    }
}

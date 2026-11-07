plugins {
    `java-platform`
    `maven-publish`
}

description = "Aligns the versions of every VxHub artifact."

val publishGroup = providers.gradleProperty("vxhub.group").get()
val publishVersion = providers.gradleProperty("version").orNull
    ?.takeIf { it.isNotBlank() && it != "unspecified" }
    ?: providers.gradleProperty("vxhub.version").get()

group = publishGroup
version = publishVersion

dependencies {
    constraints {
        listOf(
            "vxhub", "vxhub-core", "vxhub-ui", "vxhub-revenuecat", "vxhub-firebase", "vxhub-amplitude",
            "vxhub-appsflyer", "vxhub-onesignal", "vxhub-sentry", "vxhub-facebook", "vxhub-google-signin", "vxhub-media",
        ).forEach { api("$publishGroup:$it:$publishVersion") }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "vxhub-bom"
            from(components["javaPlatform"])
        }
    }
}

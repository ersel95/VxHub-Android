pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "vxhub-android"

// Published artifacts. Each satellite wraps exactly one third-party SDK, so an app pays (in method
// count, startup work and privacy disclosures) only for the services it links — the same split as
// the iOS package's products.
include(
    ":vxhub-core",
    ":vxhub-ui",
    ":vxhub-revenuecat",
    ":vxhub-firebase",
    ":vxhub-amplitude",
    ":vxhub-appsflyer",
    ":vxhub-onesignal",
    ":vxhub-sentry",
    ":vxhub-facebook",
    ":vxhub-google-signin",
)

// Not published.
// include(":sample")

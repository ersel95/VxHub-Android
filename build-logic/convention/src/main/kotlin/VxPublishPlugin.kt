import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * Publishes the module's `release` variant as `<vxhub.group>:<module name>:<version>`.
 *
 * The version is the git tag on a release build (JitPack and CI pass `-Pversion=<tag>`) and
 * `vxhub.version` from gradle.properties otherwise, so cutting a release is "tag, push" with no
 * file edits — the same flow as Collie and Olaf.
 */
class VxPublishPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("maven-publish")

        val publishGroup = providers.gradleProperty("vxhub.group").get()
        val publishVersion = providers.gradleProperty("version").orNull
            ?.takeIf { it.isNotBlank() && it != "unspecified" }
            ?: providers.gradleProperty("vxhub.version").get()

        group = publishGroup
        version = publishVersion

        extensions.configure<PublishingExtension> {
            publications {
                register<MavenPublication>("release") {
                    groupId = publishGroup
                    artifactId = project.name
                    version = publishVersion

                    afterEvaluate {
                        from(components.getByName("release"))
                    }

                    pom {
                        name.set(project.name)
                        description.set(
                            project.description
                                ?: "VxHub for Android — server-driven monetization and engagement SDK.",
                        )
                        url.set("https://github.com/ersel95/VxHub-Android")
                        developers {
                            developer {
                                id.set("ersel95")
                                name.set("Ersel Tarhan")
                            }
                        }
                        scm {
                            url.set("https://github.com/ersel95/VxHub-Android")
                        }
                    }
                }
            }
        }
    }
}

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Compose for a VxHub library module. Apply after `vxhub.android.library`. */
class VxAndroidComposePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<LibraryExtension> {
            buildFeatures {
                compose = true
            }
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            val bom = platform(libs.findLibrary("compose-bom").get())
            add("implementation", bom)
            add("testImplementation", bom)
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
        }
    }
}

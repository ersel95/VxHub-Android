import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Shared setup for every published VxHub module.
 *
 * - Kotlin comes from AGP's built-in support (AGP 9+), so `org.jetbrains.kotlin.android` is never
 *   applied; KGP's version is the one the root build puts on the classpath.
 * - Explicit API mode: a public SDK must not leak a symbol by forgetting `internal`.
 * - Warnings are errors, the same bar the iOS package keeps.
 * - Compiled with the newest Kotlin, but for `kotlinConsumer`'s language/API level and stdlib, so
 *   apps on that Kotlin (AGP 9's built-in one) can read VxHub's metadata.
 */
class VxAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        fun version(alias: String): Int = libs.findVersion(alias).get().requiredVersion.toInt()

        extensions.configure<LibraryExtension> {
            compileSdk = version("compileSdk")
            compileSdkMinor = version("compileSdkMinor")

            defaultConfig {
                minSdk = version("minSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                // Every module ships the keep rules its own reflection or serialization needs, so a
                // host app's R8 never has to know VxHub's internals.
                consumerProguardFiles("consumer-rules.pro")
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            testOptions {
                unitTests {
                    isReturnDefaultValues = true
                    isIncludeAndroidResources = true
                }
            }

            lint {
                abortOnError = true
                checkReleaseBuilds = true
            }

            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        val consumerKotlin = libs.findVersion("kotlinConsumer").get().requiredVersion
        val consumerLevel = KotlinVersion.fromVersion(consumerKotlin.split('.').take(2).joinToString("."))
        extensions.configure<KotlinBaseExtension> {
            coreLibrariesVersion = consumerKotlin
        }

        tasks.withType<KotlinJvmCompile>().configureEach {
            val isTestCompilation = name.contains("Test")
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                languageVersion.set(consumerLevel)
                apiVersion.set(consumerLevel)
                allWarningsAsErrors.set(true)
                if (!isTestCompilation) {
                    freeCompilerArgs.add("-Xexplicit-api=strict")
                }
            }
        }
    }
}

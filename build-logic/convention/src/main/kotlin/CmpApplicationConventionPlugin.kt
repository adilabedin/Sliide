import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/** `:composeApp`: `cmp.library` + Koin + the static `ComposeApp` framework consumed by iosApp. */
class CmpApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.androidlearning.sliide.convention.cmp.library")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                targets.withType(KotlinNativeTarget::class.java).configureEach {
                    binaries.framework {
                        baseName = "ComposeApp"
                        isStatic = true
                    }
                }
            }

            dependencies {
                "commonMainImplementation"(platform(libs.library("koin-bom")))
                "commonMainImplementation"(libs.library("koin-core"))
                "commonMainImplementation"(libs.library("koin-compose"))
                "commonMainImplementation"(libs.library("koin-compose-viewmodel"))
                "androidMainImplementation"(libs.library("koin-android"))
            }
        }
    }
}

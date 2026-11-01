import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.library
import com.androidlearning.sliide.convention.pathToPackageName
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension

/** `kmp.library` + Compose Multiplatform, Material 3, Compose resources and Android preview tooling. */
class CmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.androidlearning.sliide.convention.kmp.library")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("org.jetbrains.compose")
            }

            extensions.configure<ComposeExtension> {
                (this as ExtensionAware).extensions.configure<ResourcesExtension> {
                    packageOfResClass = "${pathToPackageName()}.generated.resources"
                }
            }

            dependencies {
                "commonMainImplementation"(libs.library("compose-runtime"))
                "commonMainImplementation"(libs.library("compose-foundation"))
                "commonMainImplementation"(libs.library("compose-material3"))
                "commonMainImplementation"(libs.library("compose-ui"))
                "commonMainImplementation"(libs.library("compose-components-resources"))
                "commonMainImplementation"(libs.library("compose-uiToolingPreview"))

                // KMP library modules have no debug variant, so preview tooling goes on androidMain.
                "androidMainImplementation"(libs.library("compose-uiTooling"))

                // Compose UI tests on a device: the test manifest supplies the host ComponentActivity.
                "androidDeviceTestImplementation"(libs.library("compose-uiTestJunit4"))
                "androidDeviceTestImplementation"(libs.library("compose-uiTestManifest"))
                // ui-test-junit4 pulls an Espresso too old for API 34+ (InputManager.getInstance was removed).
                "androidDeviceTestImplementation"(libs.library("androidx-espresso-core"))
            }
        }
    }
}

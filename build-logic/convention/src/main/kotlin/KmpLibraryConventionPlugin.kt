import com.androidlearning.sliide.convention.configureKotlinMultiplatform
import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Base plugin for every KMP module: Android + iOS targets, serialization, coroutines and test libraries. */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            configureKotlinMultiplatform()

            dependencies {
                "commonMainImplementation"(libs.library("kotlinx-coroutines-core"))
                "commonMainImplementation"(libs.library("kotlinx-serialization-json"))

                "commonTestImplementation"(libs.library("kotlin-test"))
                "commonTestImplementation"(libs.library("assertk"))
                "commonTestImplementation"(libs.library("turbine"))
                "commonTestImplementation"(libs.library("kotlinx-coroutines-test"))

                "androidDeviceTestImplementation"(libs.library("androidx-testRunner"))
                "androidDeviceTestImplementation"(libs.library("androidx-testExt-junit"))
            }
        }
    }
}

import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Presentation modules: `cmp.library` + design system, Koin and lifecycle/ViewModel. */
class CmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.androidlearning.sliide.convention.cmp.library")
            }

            dependencies {
                "commonMainImplementation"(project(":core:designsystem"))

                "commonMainImplementation"(platform(libs.library("koin-bom")))
                "commonMainImplementation"(libs.library("koin-core"))
                "commonMainImplementation"(libs.library("koin-compose"))
                "commonMainImplementation"(libs.library("koin-compose-viewmodel"))

                "commonMainImplementation"(libs.library("androidx-lifecycle-viewmodelCompose"))
                "commonMainImplementation"(libs.library("androidx-lifecycle-runtimeCompose"))
            }
        }
    }
}

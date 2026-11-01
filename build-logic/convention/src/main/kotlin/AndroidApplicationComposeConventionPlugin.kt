import com.android.build.api.dsl.ApplicationExtension
import com.androidlearning.sliide.convention.libs
import com.androidlearning.sliide.convention.library
import com.androidlearning.sliide.convention.versionInt
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** Thin Android application shell with Compose. */
class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            extensions.configure<ApplicationExtension> {
                namespace = "com.androidlearning.sliide"
                compileSdk = libs.versionInt("android-compileSdk")

                defaultConfig {
                    applicationId = "com.androidlearning.sliide"
                    minSdk = libs.versionInt("android-minSdk")
                    targetSdk = libs.versionInt("android-targetSdk")
                    versionCode = 1
                    versionName = "1.0"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = false
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                buildFeatures {
                    compose = true
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }

            dependencies {
                "implementation"(libs.library("compose-uiToolingPreview"))
                "debugImplementation"(libs.library("compose-uiTooling"))
            }
        }
    }
}

package com.androidlearning.sliide.convention

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Shared KMP setup for every module: Android library target (with host + device tests) and the
 * iOS targets. Frameworks are only produced by `:composeApp`.
 */
internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        (this as ExtensionAware).extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>().apply {
            namespace = pathToPackageName()
            compileSdk = libs.versionInt("android-compileSdk")
            minSdk = libs.versionInt("android-minSdk")
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
            androidResources {
                enable = true
            }
            withHostTest {
                isIncludeAndroidResources = true
            }
            withDeviceTestBuilder {
                sourceSetTreeName = "test"
            }.configure {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }

        iosArm64()
        iosSimulatorArm64()

        applyDefaultHierarchyTemplate()

        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
            optIn.add("kotlin.time.ExperimentalTime")
        }
    }
}

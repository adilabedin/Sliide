plugins {
    alias(libs.plugins.convention.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.users.domain)
            implementation(projects.core.database)
            implementation(projects.core.data)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.work.runtime)
            implementation(libs.koin.androidx.workmanager)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.work.testing)
        }
    }
}

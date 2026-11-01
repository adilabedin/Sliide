plugins {
    alias(libs.plugins.convention.cmp.application)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            implementation(projects.core.database)
            implementation(projects.core.data)
            implementation(libs.kotlinx.coroutines.core)
            implementation(projects.users.domain)
            implementation(projects.users.data)
            implementation(projects.users.presentation)
        }
        getByName("androidHostTest").dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test)
            implementation(libs.androidx.work.runtime)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

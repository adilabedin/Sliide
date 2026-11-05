plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.users.domain)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.compose.ui.backhandler)
        }
    }
}

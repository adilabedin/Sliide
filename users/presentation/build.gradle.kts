plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.users.domain)
            implementation(libs.compose.material3.adaptive.layout)
            implementation(libs.compose.material3.adaptive.navigation)
            implementation(libs.compose.ui.backhandler)
        }
    }
}

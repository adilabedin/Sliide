plugins {
    alias(libs.plugins.convention.cmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.material3.adaptive)
            api(libs.compose.material.icons.core)
        }
    }
}

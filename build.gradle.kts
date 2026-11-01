plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.buildkonfig) apply false
    alias(libs.plugins.ktlint) apply false
}

// Lint gate: every module gets ktlintCheck / ktlintFormat. Generated sources (Compose resources,
// Room/KSP) are never linted. New code must be clean. Run by .claude/hooks/stop-lint.sh.
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        baseline.set(file("ktlint-baseline.xml"))
        filter {
            exclude { it.file.path.contains("${File.separator}build${File.separator}") }
        }
    }
}

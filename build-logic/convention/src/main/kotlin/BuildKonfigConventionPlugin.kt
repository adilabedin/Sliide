import com.androidlearning.sliide.convention.pathToPackageName
import com.codingfeline.buildkonfig.compiler.FieldSpec
import com.codingfeline.buildkonfig.gradle.BuildKonfigExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.util.Properties

/**
 * BuildKonfig with the GoRest credentials. Generates an `internal object BuildKonfig` in the module's package.
 *
 * `API_TOKEN` comes from `GOREST_API_TOKEN` in the git-ignored root `local.properties`, then the environment variable
 * of the same name (CI), then an empty string with a warning so clean checkouts and tests still build. The token is
 * never read from, or written to, a tracked file. Apply on top of `kmp.library`.
 */
class BuildKonfigConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.codingfeline.buildkonfig")

            val apiToken =
                localProperty(TOKEN_KEY)
                    ?: providers.environmentVariable(TOKEN_KEY).orNull
                    ?: "".also {
                        logger.warn(
                            "w: $TOKEN_KEY is not set in local.properties or the environment; " +
                                "GoRest writes will fail with 401.",
                        )
                    }

            extensions.configure<BuildKonfigExtension> {
                packageName.set(pathToPackageName())
                defaultConfigs {
                    buildConfigField(FieldSpec.Type.STRING, "BASE_URL", BASE_URL)
                    buildConfigField(FieldSpec.Type.STRING, "API_TOKEN", apiToken)
                }
            }
        }
    }

    private fun Project.localProperty(key: String): String? {
        val file = rootProject.file("local.properties")
        if (!file.isFile) return null
        val properties = Properties().apply { file.inputStream().use(::load) }
        return properties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val TOKEN_KEY = "GOREST_API_TOKEN"
        const val BASE_URL = "https://gorest.co.in/public/v2/"
    }
}

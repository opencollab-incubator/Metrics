import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.kyori.indra.git.IndraGitExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import java.io.File
import java.net.URL

val providedDependencies = mutableMapOf<String, MutableSet<String>>()

fun Project.provided(pattern: String, name: String, excludedOn: Int = 0b110) {
    providedDependencies.getOrPut(project.name) { mutableSetOf() }
        .add("${calcExclusion(pattern, 0b100, excludedOn)}:${calcExclusion(name, 0b10, excludedOn)}")
}

fun Project.provided(dependency: ProjectDependency) =
    provided(dependency.group!!, dependency.name)

fun Project.provided(dependency: MinimalExternalModuleDependency) =
    provided(dependency.module.group, dependency.module.name)

fun Project.provided(provider: Provider<MinimalExternalModuleDependency>) =
    provided(provider.get())

fun Project.branchName(): String =
    the<IndraGitExtension>().branchName().orNull ?: System.getenv("BRANCH_NAME") ?: "local/dev"

fun Project.shouldAddBranchName(): Boolean {
    return branchName() !in arrayOf("master", "local/dev")
}

/**
 * Helper to ensure we never override the latest releases
 *
 * Examples:
 * - "api/2.9.3" -> "preview-api-2.9.3-SNAPSHOT"
 * - "feature/custom-entities-api" -> "preview-feature-custom-entities-api-SNAPSHOT"
 */
fun Project.versionWithBranchName(): String {
    val branch = branchName()
    val parts = branch.split('/')
    val prefix = parts.getOrNull(0)?.replace(Regex("[^0-9A-Za-z-]"), "-") ?: branch
    val versionNum = parts.getOrNull(1)?.replace(Regex("[^0-9A-Za-z.-]"), "-") ?: version.toString()
    return "preview-$prefix-$versionNum-SNAPSHOT"
}

private fun calcExclusion(section: String, bit: Int, excludedOn: Int): String =
    if (excludedOn and bit > 0) section else ""

fun projectVersion(project: Project): String =
    project.version.toString().replace("SNAPSHOT", "b" + buildNumber())

fun buildNumber(): Int =
    (System.getenv("BUILD_NUMBER"))?.let { Integer.parseInt(it) } ?: -1


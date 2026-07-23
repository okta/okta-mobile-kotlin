import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Registers Kotlin Binary Compatibility Validator (BCV) API dump and check tasks by hand, for
 * targets the standalone `binary-compatibility-validator` plugin can't see.
 *
 * BCV only creates its `apiDump`/`apiCheck` tasks when it detects the standalone `kotlin-android`,
 * `kotlin`, or `kotlin-multiplatform` plugin applied via `pluginManager.withPlugin(...)`. Two of
 * our targets never trigger that:
 *  - Our four Android-only modules use AGP 9's built-in Kotlin, which applies none of those IDs
 *  - auth-foundation/oauth2's KMP `android` target compiles via AGP's own
 *    `com.android.kotlin.multiplatform.library` plugin, whose target class KGP's newer built-in
 *    `abiValidation` doesn't recognize either (it filters for `KotlinAndroidTarget`).
 *
 * This plugin keeps whichever Kotlin set up a module already has and registers BCV's own task
 * types directly, fed by the relevant compile task's output classes
 *
 * [KotlinApiBuildTask] and [KotlinApiCompareTask] are BCV-internal, not public API, so this is
 * pinned to the exact BCV version below. This is a stopgap until Android support lands in KGP's
 * built-in ABI validation (tracked upstream, unscheduled, in KT-78025).
 */
class BinaryCompatValidationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("binaryCompatValidationExtension", BinaryCompatValidationExtension::class.java)
        ext.variant.convention("release")
        ext.taskNamePrefix.convention(ext.variant)
        ext.kotlinCompileTaskName.convention(ext.variant.map { "compile${it.replaceFirstChar(Char::uppercase)}Kotlin" })
        ext.javaCompileTaskName.convention(ext.variant.map { "compile${it.replaceFirstChar(Char::uppercase)}JavaWithJavac" })
        ext.apiFile.convention(project.layout.projectDirectory.file("api/${project.name}.api"))

        // Defer until the compile tasks this reads from have been registered.
        project.afterEvaluate {
            val prefix = ext.taskNamePrefix.get()

            // KotlinApiBuildTask runs inside an isolated classloader worker, which needs its own
            // classpath - it doesn't inherit the plugin's. BCV's own POM only declares java-diff-utils;
            // ASM (ClassNode) and kotlin-metadata-jvm (reading @Metadata for Kotlin visibility) are
            // needed at runtime too but aren't declared, so add them explicitly.
            val bcvRuntimeClasspath =
                project.configurations.detachedConfiguration(
                    project.dependencies.create("org.jetbrains.kotlinx:binary-compatibility-validator:0.18.1"),
                    project.dependencies.create("org.jetbrains.kotlin:kotlin-metadata-jvm:2.1.0"),
                    project.dependencies.create("org.ow2.asm:asm:9.7"),
                    project.dependencies.create("org.ow2.asm:asm-tree:9.7")
                )

            val apiBuild =
                project.tasks.register<KotlinApiBuildTask>("${prefix}ApiBuild") {
                    inputClassesDirs.from(project.tasks.named(ext.kotlinCompileTaskName.get()).map { it.outputs.files })
                    val javaTaskName = ext.javaCompileTaskName.get()
                    if (javaTaskName.isNotBlank() && project.tasks.findByName(javaTaskName) != null) {
                        inputClassesDirs.from(project.tasks.named(javaTaskName).map { it.outputs.files })
                    }
                    ignoredClasses.set(ext.ignoredClasses)
                    runtimeClasspath.from(bcvRuntimeClasspath)
                    outputApiFile.set(project.layout.buildDirectory.file("bcv/$prefix/${project.name}.api"))
                }

            val apiCheck =
                project.tasks.register<KotlinApiCompareTask>("${prefix}ApiCheck") {
                    projectApiFile.set(ext.apiFile)
                    generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
                }

            project.tasks.register<ApiDumpTask>("${prefix}ApiDump") {
                group = "verification"
                description = "Updates the committed BCV API dump for the $prefix target."
                generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
                committedApiFile.set(ext.apiFile)
            }

            project.tasks.named<Task>(LifecycleBasePlugin.CHECK_TASK_NAME).configure { dependsOn(apiCheck) }
        }
    }
}

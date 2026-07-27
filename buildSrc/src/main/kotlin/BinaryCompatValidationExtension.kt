import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * Configuration for the `BinaryCompatValidationExtension { }` block.
 *
 * Defaults match a plain AGP library module's `release` variant. Modules whose public API
 * comes from a differently-named compile task (e.g. a KMP target's `compileAndroidMain`) should
 * override [kotlinCompileTaskName] and [taskNamePrefix] directly instead of [variant].
 */
abstract class BinaryCompatValidationExtension {
    /** Used to derive default task names below and to name the tasks this plugin registers. */
    abstract val variant: Property<String>

    /** Name of the registered `<prefix>ApiBuild`/`<prefix>ApiCheck`/`<prefix>ApiDump` tasks. Defaults to [variant]. */
    abstract val taskNamePrefix: Property<String>

    /** Compile task whose output classes make up the public API. Defaults to `compile<Variant>Kotlin`. */
    abstract val kotlinCompileTaskName: Property<String>

    /**
     * Java compile task to additionally include, if any. Defaults to `compile<Variant>JavaWithJavac`.
     * Set to an empty string for Kotlin-only source sets (e.g. KMP targets), where no such task exists.
     */
    abstract val javaCompileTaskName: Property<String>

    /** The committed API dump the check task verifies against and the dump task writes to. Defaults to `api/<module>.api`. */
    abstract val apiFile: RegularFileProperty

    /** Fully qualified class names to exclude from the dump, e.g. a generated BuildConfig class. */
    abstract val ignoredClasses: SetProperty<String>
}

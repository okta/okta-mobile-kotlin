import com.diffplug.spotless.FormatterFunc
import java.io.Serializable

/**
 * A Spotless [FormatterFunc] that rejects Java star imports.
 *
 * Star imports (e.g., `import java.util.*;` or `import static org.junit.Assert.*;`)
 * reduce readability and can cause ambiguous name resolution. This step fails the
 * Spotless check if any star imports are found.
 */
class NoStarImportsStep : FormatterFunc, Serializable {
    override fun apply(input: String): String {
        val regex = Regex("""^import\s+(?:static\s+)?\S+\.\*;""", RegexOption.MULTILINE)
        val match = regex.find(input)
        if (match != null) {
            throw AssertionError("Star imports are not allowed: ${match.value}")
        }
        return input
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

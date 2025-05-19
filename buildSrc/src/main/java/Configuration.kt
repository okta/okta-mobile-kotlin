import org.gradle.api.JavaVersion
import org.gradle.api.Project
import java.util.Properties

const val BOM_VERSION = "2.0.3"
const val AUTH_FOUNDATION_VERSION = "2.0.3"
const val OAUTH2_VERSION = "2.0.3"
const val WEB_AUTHENTICATION_UI_VERSION = "2.0.3"
const val LEGACY_TOKEN_MIGRATION_VERSION = "2.0.3"
const val MIN_SDK = 23
const val COMPILE_SDK = 35
const val TARGET_SDK = 35
val SOURCE_COMPATIBILITY = JavaVersion.VERSION_11
val TARGET_COMPATIBILITY = JavaVersion.VERSION_11
val JVM_TARGET = JavaVersion.VERSION_11.toString()

data class OssrhCredentials(
    val ossrhUsername: String,
    val ossrhPassword: String,
    val signingKeyId: String,
    val signingPassword: String,
    private val signingKeyBase64: String,
) {
    val signingKey: String by lazy { java.util.Base64.getDecoder().decode(signingKeyBase64).toString(Charsets.UTF_8) }
}

enum class Modules(val moduleName: String) {
    AUTH_FOUNDATION("auth-foundation"),
    WEB_AUTHENTICATION_UI("web-authentication-ui"),
    OAUTH2("oauth2"),
    LEGACY_TOKEN_MIGRATION("legacy-token-migration"),
    BOM("bom"),
}

fun pomName(project: Project): String = when (project.name) {
    Modules.AUTH_FOUNDATION.moduleName -> "Okta Mobile Kotlin - Auth Foundation"
    Modules.OAUTH2.moduleName -> "Okta Mobile Kotlin - OAuth2"
    Modules.WEB_AUTHENTICATION_UI.moduleName -> "Okta Mobile Kotlin - Web Authentication UI"
    Modules.LEGACY_TOKEN_MIGRATION.moduleName -> "Okta Mobile Kotlin - Legacy Token Migration"
    Modules.BOM.moduleName -> "Okta Mobile Kotlin - Bill of Materials"
    else -> throw IllegalArgumentException("Unknown module ${project.name}")
}

fun ossrhCredentials(project: Project): OssrhCredentials {
    val properties = Properties()
    val propFile = project.rootProject.file("local.properties")
    if (propFile.exists()) propFile.inputStream().use { properties.load(it) }

    return OssrhCredentials(
        properties.getProperty("ossrh.username") ?: System.getenv("SONATYPE_USERNAME") ?: project.properties["mavenCentralUsername"] as? String ?: "",
        properties.getProperty("ossrh.password") ?: System.getenv("SONATYPE_PASSWORD") ?: project.properties["mavenCentralPassword"] as? String ?: "",
        properties.getProperty("signing.keyId") ?: System.getenv("GPG_KEYID") ?: "",
        properties.getProperty("signing.password") ?: System.getenv("GPG_PASSPHRASE") ?: "",
        properties.getProperty("signing.key") ?: System.getenv("SIGNING_KEY") ?: "",
    )
}

fun releaseVersion(project: Project): String = when (project.name) {
    Modules.AUTH_FOUNDATION.moduleName -> AUTH_FOUNDATION_VERSION
    Modules.OAUTH2.moduleName -> OAUTH2_VERSION
    Modules.WEB_AUTHENTICATION_UI.moduleName -> WEB_AUTHENTICATION_UI_VERSION
    Modules.LEGACY_TOKEN_MIGRATION.moduleName -> LEGACY_TOKEN_MIGRATION_VERSION
    Modules.BOM.moduleName -> BOM_VERSION
    else -> throw IllegalArgumentException("Unknown module ${project.name}")
}

fun parseScheme(redirectUri: String): String {
    val scheme = redirectUri.split("://").firstOrNull()
    return scheme ?: throw IllegalArgumentException("Invalid redirect URI: $redirectUri")
}

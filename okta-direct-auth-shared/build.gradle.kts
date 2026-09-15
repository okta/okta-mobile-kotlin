import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    id("spotless")
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            rootProject.file("local.properties").inputStream().use { load(it) }
        }
    }

val issuer = localProperties.getProperty("issuer") ?: ""
val clientId = localProperties.getProperty("clientId") ?: ""
val authorizationServerId = localProperties.getProperty("authorizationServerId") ?: ""
val signInRedirectUri = localProperties.getProperty("signInRedirectUri") ?: ""
val desktopSignInRedirectUri = localProperties.getProperty("desktopSignInRedirectUri") ?: ""

// Confidential-client testing only (see PlatformClientAuthentication.kt) — baked in for Android
// only because an installed app has no access to the developer machine's local.properties at
// runtime. Never do this for a real app; a secret embedded in a shipped APK can be extracted from
// it.
val clientSecret = localProperties.getProperty("clientSecret") ?: ""
val clientAssertionPrivateKeyPem = localProperties.getProperty("clientAssertionPrivateKeyPem") ?: ""
// The registered key's Key ID (as shown for it in the app integration's Public Keys admin
// console tab) — private_key_jwt requires the assertion's JWT header to carry this so Okta knows
// which registered key to verify against when the client has more than one active key. Only
// meaningful alongside clientAssertionPrivateKeyPem.
val clientAssertionKid = localProperties.getProperty("clientAssertionKid") ?: ""

// Cross App Access (XAA) — resource app target, all optional. Absence of any of these must never
// fail the build; the sample simply reports Cross App Access as not configured. See
// CrossAppAccessConfig.kt for validation and CrossAppAccessCredential.kt for the target credential,
// which follows the same testing-only, per-platform sourcing as clientSecret/
// clientAssertionPrivateKeyPem above.
val xaaTargetIssuer = localProperties.getProperty("xaaTargetIssuer") ?: ""
val xaaTargetAuthorizationServerId = localProperties.getProperty("xaaTargetAuthorizationServerId") ?: ""
val xaaTargetClientId = localProperties.getProperty("xaaTargetClientId") ?: ""
val xaaTargetClientSecret = localProperties.getProperty("xaaTargetClientSecret") ?: ""
val xaaTargetClientAssertionPrivateKeyPem = localProperties.getProperty("xaaTargetClientAssertionPrivateKeyPem") ?: ""
// The registered key's Key ID (as shown for it in the app integration's Public Keys admin
// console tab) — private_key_jwt requires the assertion's JWT header to carry this so Okta knows
// which registered key to verify against; omitting it fails with "The client_assertion JWT kid is
// invalid." Only meaningful alongside xaaTargetClientAssertionPrivateKeyPem.
val xaaTargetClientAssertionKid = localProperties.getProperty("xaaTargetClientAssertionKid") ?: ""
val xaaTargetResource = localProperties.getProperty("xaaTargetResource") ?: ""

// Cross App Access (XAA) — the requesting app's OWN identity, separate from the primary app
// above. The ID-JAG exchange (first step) must be submitted to the org's own authorization
// server, never a custom one — so there is deliberately no xaaIdpAuthorizationServerId key.
// Registering this as a second, independent app integration in Okta (rather than reusing the
// primary app/authorizationServerId) is what lets a developer test the primary flows against a
// custom authorization server and Cross App Access in the same running app.
val xaaIdpIssuer = localProperties.getProperty("xaaIdpIssuer") ?: ""
val xaaIdpClientId = localProperties.getProperty("xaaIdpClientId") ?: ""
val xaaIdpClientSecret = localProperties.getProperty("xaaIdpClientSecret") ?: ""
val xaaIdpClientAssertionPrivateKeyPem = localProperties.getProperty("xaaIdpClientAssertionPrivateKeyPem") ?: ""
// See xaaTargetClientAssertionKid above for why this is needed.
val xaaIdpClientAssertionKid = localProperties.getProperty("xaaIdpClientAssertionKid") ?: ""

val isCi = System.getenv("CI")?.toBoolean() ?: false
if (!isCi && (issuer.isEmpty() || clientId.isEmpty() || authorizationServerId.isEmpty())) {
    throw GradleException(
        "Missing required properties in local.properties. Please add the following:\n" +
            "issuer=<your_issuer>\n" +
            "clientId=<your_client_id>\n" +
            "authorizationServerId=<your_authorization_server_id>\n" +
            "signInRedirectUri=<android_custom_scheme_uri> (optional, for Android browser sign-in)\n" +
            "desktopSignInRedirectUri=<localhost_uri> (optional, for Desktop browser sign-in, e.g. http://localhost:8080/callback)\n" +
            "Direct Auth configuration: https://developer.okta.com/docs/guides/configure-direct-auth-grants"
    )
}

// Escapes a value for embedding inside a double-quoted Kotlin string literal in generated source
// (needed for clientAssertionPrivateKeyPem, which may contain literal newlines).
fun escapeForKotlinStringLiteral(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("$", "\\$")
        .replace("\"", "\\\"")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

// Generate AppConfig.kt with local.properties values for all KMP targets (replaces Android BuildConfig).
val generateAppConfig =
    tasks.register("generateAppConfig") {
        description = "Generates AppConfig.kt with local.properties values."
        group = "build"

        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            inputs.file(localPropertiesFile)
        }
        val outputDir = layout.buildDirectory.dir("generated/source/appConfig/kotlin")
        outputs.dir(outputDir)

        doLast {
            val outputFile = outputDir.get().file("com/okta/directauth/app/AppConfig.kt").asFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(
                """
            |package com.okta.directauth.app
            |
            |object AppConfig {
            |    const val ISSUER: String = "$issuer"
            |    const val CLIENT_ID: String = "$clientId"
            |    const val AUTHORIZATION_SERVER_ID: String = "$authorizationServerId"
            |    const val SIGN_IN_REDIRECT_URI: String = "$signInRedirectUri"
            |    const val DESKTOP_SIGN_IN_REDIRECT_URI: String = "$desktopSignInRedirectUri"
            |    const val CLIENT_SECRET: String = "${escapeForKotlinStringLiteral(clientSecret)}"
            |    const val CLIENT_ASSERTION_PRIVATE_KEY_PEM: String = "${escapeForKotlinStringLiteral(clientAssertionPrivateKeyPem)}"
            |    const val CLIENT_ASSERTION_KID: String = "${escapeForKotlinStringLiteral(clientAssertionKid)}"
            |    const val XAA_TARGET_ISSUER: String = "$xaaTargetIssuer"
            |    const val XAA_TARGET_AUTHORIZATION_SERVER_ID: String = "$xaaTargetAuthorizationServerId"
            |    const val XAA_TARGET_CLIENT_ID: String = "$xaaTargetClientId"
            |    const val XAA_TARGET_CLIENT_SECRET: String = "${escapeForKotlinStringLiteral(xaaTargetClientSecret)}"
            |    const val XAA_TARGET_CLIENT_ASSERTION_PRIVATE_KEY_PEM: String = "${escapeForKotlinStringLiteral(xaaTargetClientAssertionPrivateKeyPem)}"
            |    const val XAA_TARGET_CLIENT_ASSERTION_KID: String = "${escapeForKotlinStringLiteral(xaaTargetClientAssertionKid)}"
            |    const val XAA_TARGET_RESOURCE: String = "$xaaTargetResource"
            |    const val XAA_IDP_ISSUER: String = "$xaaIdpIssuer"
            |    const val XAA_IDP_CLIENT_ID: String = "$xaaIdpClientId"
            |    const val XAA_IDP_CLIENT_SECRET: String = "${escapeForKotlinStringLiteral(xaaIdpClientSecret)}"
            |    const val XAA_IDP_CLIENT_ASSERTION_PRIVATE_KEY_PEM: String = "${escapeForKotlinStringLiteral(xaaIdpClientAssertionPrivateKeyPem)}"
            |    const val XAA_IDP_CLIENT_ASSERTION_KID: String = "${escapeForKotlinStringLiteral(xaaIdpClientAssertionKid)}"
            |}
                """.trimMargin()
            )
        }
    }

kotlin {
    android {
        namespace = "com.okta.directauth.app"
        compileSdk = COMPILE_SDK
        minSdk = 28
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
                }
            }
        }
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppConfig.map { it.outputs.files.singleFile })
            dependencies {
                implementation(project(":okta-direct-auth"))
                implementation(project(":auth-foundation"))
                implementation(project(":oauth2"))
                implementation(libs.ktor.client.logging)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.animation)
                implementation(libs.jetbrains.compose.material.icons.extended)
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.ui.tooling.preview)
                implementation(libs.components.resources)

                implementation(libs.jjwt.api)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.coroutines.core)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            }
        }

        androidMain.dependencies {
            implementation(project(":web-authentication-ui"))
            implementation(libs.androidx.activity.compose)
            implementation(libs.core.ktx)
            implementation(libs.lifecycle.runtime.ktx)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.compose.ui.tooling)
        }

        jvmMain.dependencies {
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)
    // jjwt runtime deps for Android (provided via desktopMain.dependencies for desktop)
    "androidMainRuntimeOnly"(libs.jjwt.impl)
    "androidMainRuntimeOnly"(libs.jjwt.jackson)
}

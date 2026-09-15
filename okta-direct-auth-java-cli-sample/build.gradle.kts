import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    id("spotless")
}

java {
    sourceCompatibility = SOURCE_COMPATIBILITY
    targetCompatibility = TARGET_COMPATIBILITY
}

kotlin {
    compilerOptions {
        jvmTarget =
            org.jetbrains.kotlin.gradle.dsl.JvmTarget
                .fromTarget(JVM_TARGET)
    }
}

application {
    mainClass.set("com.okta.directauth.cli.Main")
    applicationName = "okta-direct-auth-cli"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }

val issuer = localProperties.getProperty("issuer") ?: ""
val clientId = localProperties.getProperty("clientId") ?: ""
val authorizationServerId = localProperties.getProperty("authorizationServerId") ?: ""
val signInRedirectUri = localProperties.getProperty("signInRedirectUri") ?: ""
val desktopSignInRedirectUri = localProperties.getProperty("desktopSignInRedirectUri") ?: ""

// Cross App Access (XAA) — resource app target, all optional; absence must never fail the build.
// Only the non-credential values are baked here, mirroring issuer/clientId/authorizationServerId
// above. The target credential (xaaTargetClientSecret / xaaTargetClientAssertionPrivateKeyPem) is
// deliberately NOT baked into AppConfig — like this sample's own clientSecret/
// clientAssertionPrivateKeyPem, it is read from local.properties at runtime by
// ClientAuthentication instead, so a secret never lands in generated build output.
val xaaTargetIssuer = localProperties.getProperty("xaaTargetIssuer") ?: ""
val xaaTargetAuthorizationServerId = localProperties.getProperty("xaaTargetAuthorizationServerId") ?: ""
val xaaTargetClientId = localProperties.getProperty("xaaTargetClientId") ?: ""
val xaaTargetResource = localProperties.getProperty("xaaTargetResource") ?: ""

// Cross App Access (XAA) — the requesting app's OWN identity, separate from the primary app
// above. The ID-JAG exchange (first step) must be submitted to the org's own authorization
// server, never a custom one — so there is deliberately no xaaIdpAuthorizationServerId key.
// Registering this as a second, independent app integration in Okta (rather than reusing the
// primary app/authorizationServerId) is what lets a developer test the primary flows against a
// custom authorization server and Cross App Access in the same run. The credential itself
// (xaaIdpClientSecret / xaaIdpClientAssertionPrivateKeyPem) is deliberately NOT baked here, for
// the same reason as xaaTargetClientSecret above.
val xaaIdpIssuer = localProperties.getProperty("xaaIdpIssuer") ?: ""
val xaaIdpClientId = localProperties.getProperty("xaaIdpClientId") ?: ""

val isCi = System.getenv("CI")?.toBoolean() ?: false
if (!isCi && (issuer.isEmpty() || clientId.isEmpty() || authorizationServerId.isEmpty())) {
    logger.warn(
        "Missing required properties in local.properties. Please add the following:\n" +
            "issuer=<your_issuer>\n" +
            "clientId=<your_client_id>\n" +
            "authorizationServerId=<your_authorization_server_id>\n" +
            "signInRedirectUri=<android_custom_scheme_uri> (optional, for Android browser sign-in)\n" +
            "desktopSignInRedirectUri=<localhost_uri> (optional, for JVM browser sign-in, e.g. http://localhost:8080/callback)\n" +
            "Direct Auth configuration: https://developer.okta.com/docs/guides/configure-direct-auth-grants"
    )
}

val generateAppConfig =
    tasks.register("generateAppConfig") {
        description = "Generates AppConfig.java with local.properties values."
        group = "build"

        val outputDir = layout.buildDirectory.dir("generated/source/appConfig/java")
        outputs.dir(outputDir)
        doLast {
            val outputFile = outputDir.get().file("com/okta/directauth/cli/AppConfig.java").asFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(
                """
            |package com.okta.directauth.cli;
            |
            |public final class AppConfig {
            |  public static final String ISSUER = "$issuer";
            |  public static final String CLIENT_ID = "$clientId";
            |  public static final String AUTHORIZATION_SERVER_ID = "$authorizationServerId";
            |  public static final String SIGN_IN_REDIRECT_URI = "$signInRedirectUri";
            |  public static final String DESKTOP_SIGN_IN_REDIRECT_URI = "$desktopSignInRedirectUri";
            |  public static final String XAA_TARGET_ISSUER = "$xaaTargetIssuer";
            |  public static final String XAA_TARGET_AUTHORIZATION_SERVER_ID = "$xaaTargetAuthorizationServerId";
            |  public static final String XAA_TARGET_CLIENT_ID = "$xaaTargetClientId";
            |  public static final String XAA_TARGET_RESOURCE = "$xaaTargetResource";
            |  public static final String XAA_IDP_ISSUER = "$xaaIdpIssuer";
            |  public static final String XAA_IDP_CLIENT_ID = "$xaaIdpClientId";
            |
            |  private AppConfig() {}
            |}
                """.trimMargin()
            )
        }
    }

sourceSets {
    main {
        java.srcDir(generateAppConfig.map { it.outputs.files.singleFile })
    }
}

dependencies {
    implementation(project(":okta-direct-auth"))
    implementation(project(":oauth2"))
    implementation(project(":auth-foundation"))
    implementation(libs.ktor.client.core)
    implementation(libs.jjwt.api)
    implementation(libs.picocli)
    runtimeOnly(libs.ktor.client.cio)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    runtimeOnly(libs.slf4j.nop)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockito.core)
}

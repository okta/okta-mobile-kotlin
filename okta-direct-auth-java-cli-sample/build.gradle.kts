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

val isCi = System.getenv("CI")?.toBoolean() ?: false
if (!isCi && (issuer.isEmpty() || clientId.isEmpty() || authorizationServerId.isEmpty())) {
    logger.warn(
        "Missing required properties in local.properties. Please add the following:\n" +
            "issuer=<your_issuer>\n" +
            "clientId=<your_client_id>\n" +
            "authorizationServerId=<your_authorization_server_id>\n" +
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
    implementation(project(":auth-foundation"))
    implementation(libs.ktor.client.core)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    runtimeOnly(libs.slf4j.nop)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockito.core)
}

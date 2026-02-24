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

val isCi = System.getenv("CI")?.toBoolean() ?: false
if (!isCi && (issuer.isEmpty() || clientId.isEmpty() || authorizationServerId.isEmpty())) {
    throw GradleException(
        "Missing required properties in local.properties. Please add the following:\n" +
            "issuer=<your_issuer>\n" +
            "clientId=<your_client_id>\n" +
            "authorizationServerId=<your_authorization_server_id>\n" +
            "Direct Auth configuration: https://developer.okta.com/docs/guides/configure-direct-auth-grants"
    )
}

// Generate AppConfig.kt with local.properties values for all KMP targets (replaces Android BuildConfig).
val generateAppConfig =
    tasks.register("generateAppConfig") {
        description = "Generates AppConfig.kt with local.properties values."
        group = "build"

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
            |}
                """.trimMargin()
            )
        }
    }

kotlin {
    androidLibrary {
        namespace = "com.okta.directauth.app"
        compileSdk = COMPILE_SDK
        minSdk = 28
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
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
            implementation(libs.androidx.activity.compose)
            implementation(libs.core.ktx)
            implementation(libs.lifecycle.runtime.ktx)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.compose.ui.tooling)
        }

        jvmMain.dependencies {
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)
    // jjwt runtime deps for Android (provided via desktopMain.dependencies for desktop)
    "androidMainRuntimeOnly"(libs.jjwt.impl)
    "androidMainRuntimeOnly"(libs.jjwt.jackson)
}

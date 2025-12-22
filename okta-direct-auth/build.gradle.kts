plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.dokka)
    id("maven-publish")
}

// KMP androidLibrary does not generate BuildConfigs so we generate a BuildInfo.kt file instead.
val generateBuildInfoTask = tasks.register("generateBuildInfo") {
    description = "Generates BuildInfo.kt with the project version."
    group = "build"

    val outputDir = layout.buildDirectory.dir("generated/source/buildInfo/kotlin")
    outputs.dir(outputDir)

    doLast {
        val outputFile = outputDir.get().file("com/okta/directauth/BuildInfo.kt").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            |package com.okta.directauth
            |
            |internal const val SDK_VERSION: String = "okta-direct-auth-kotlin/$DIRECT_AUTH_VERSION"
            """.trimMargin()
        )
    }
}

kotlin {

    androidLibrary {
        namespace = "com.okta.directauth"
        compileSdk = COMPILE_SDK
        minSdk = MIN_SDK

        withHostTestBuilder {
            sourceSetTreeName = "test"
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfoTask)

            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.ktor.client.core)
            }
        }

        androidMain {
            dependencies {
                implementation(project(":auth-foundation"))
                implementation(libs.kotlin.serialization.json)
                implementation(libs.ktor.client.android)
                implementation(libs.ktor.client.content.negotiation)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}
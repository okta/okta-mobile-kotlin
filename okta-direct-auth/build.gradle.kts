import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kover)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    id("com.vanniktech.maven.publish.base")
    id("spotless")
    id("binary-compat-validation")
}

// KMP androidLibrary does not generate BuildConfigs so we generate a BuildInfo.kt file instead.
val generateBuildInfoTask =
    tasks.register("generateBuildInfo") {
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
    jvm()

    android {
        namespace = "com.okta.directauth"
        compileSdk = COMPILE_SDK
        minSdk = MIN_SDK

        optimization {
            consumerKeepRules.publish = true
        }

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
                implementation(project(":auth-foundation"))
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.core)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.credentials.credentials)
                implementation(libs.androidx.credentials.play.services.auth)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.coroutines.jdk8)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.ktor.client.mock.jvm)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.hamcrest)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.hamcrest)
                implementation(libs.ktor.client.mock.jvm)
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

    // Only validates the jvm target; KGP's ABI validation filters for KotlinAndroidTarget, but the androidLibrary
    // target here is a KotlinMultiplatformAndroidLibraryTargetImpl (AGP's KMP android plugin), so it's silently skipped.
    // The android target's own ABI is covered separately below, via binary-compat-validation.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                byNames.add("com.okta.directauth.BuildInfo")
            }
        }
    }
}

binaryCompatValidationExtension {
    taskNamePrefix.set("android")
    kotlinCompileTaskName.set("compileAndroidMain")
    javaCompileTaskName.set("")
    ignoredClasses.add("com.okta.directauth.BuildInfo")
}

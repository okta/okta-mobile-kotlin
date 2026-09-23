import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
                }
            }
        }
    }

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

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
                }
            }
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

    // Validates both the jvm and android targets (KGP's ABI validation now supports the KMP androidLibrary
    // target, a KotlinMultiplatformAndroidLibraryTargetImpl from AGP's KMP android plugin).
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                byNames.add("com.okta.directauth.BuildInfo")
            }
        }
    }
}

java {
    sourceCompatibility = SOURCE_COMPATIBILITY
    targetCompatibility = TARGET_COMPATIBILITY
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.dokka)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    id("com.vanniktech.maven.publish.base")
    id("spotless")
    id("binary-compatibility-validator")
}

// KMP androidLibrary does not generate BuildConfigs so we generate a BuildInfo.kt file instead.
val generateBuildInfoTask =
    tasks.register("generateBuildInfo") {
        description = "Generates BuildInfo.kt with the project version."
        group = "build"

        val outputDir = layout.buildDirectory.dir("generated/source/buildInfo/kotlin")
        outputs.dir(outputDir)

        doLast {
            val outputFile = outputDir.get().file("com/okta/oauth2/BuildInfo.kt").asFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(
                """
            |package com.okta.oauth2
            |
            |internal const val SDK_VERSION: String = "okta-oauth2-kotlin/$OAUTH2_VERSION"
                """.trimMargin()
            )
        }
    }

kotlin {
    androidLibrary {
        namespace = "com.okta.oauth2"
        compileSdk = COMPILE_SDK
        minSdk = MIN_SDK

        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(project.file("consumer-rules.pro"))
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
                    freeCompilerArgs.addAll(
                        listOf(
                            "-opt-in=kotlin.RequiresOptIn",
                            "-opt-in=com.okta.authfoundation.InternalAuthFoundationApi"
                        )
                    )
                }
            }
        }
    }

    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
                    freeCompilerArgs.addAll(
                        listOf(
                            "-opt-in=kotlin.RequiresOptIn",
                            "-opt-in=com.okta.authfoundation.InternalAuthFoundationApi"
                        )
                    )
                }
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfoTask)

            dependencies {
                api(project(":auth-foundation"))
                implementation(libs.coroutines.core)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.okio.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
            }
        }

        androidMain {
            dependencies {
                api(libs.kotlin.stdlib)
                api(libs.okhttp.core)
                api(libs.coroutines.android)
                api(libs.okio.core)

                implementation(libs.okio.jvm)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.coroutines.jdk8)
                implementation(libs.ktor.client.cio)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
                implementation(libs.junit)
            }
        }

        getByName("androidHostTest") {
            resources.srcDir("src/test/resources")
            dependencies {
                implementation(libs.coroutines.test)
                implementation(libs.junit)
                implementation(libs.truth)
                implementation(libs.kotlin.test)
                implementation(libs.mockito.core)
                implementation(libs.mockito.kotlin)
                implementation(libs.robolectric)
                implementation(libs.bcprov.jdk18on)
                implementation(libs.androidx.test.ext.junit)
                implementation(project(":test-helpers"))
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)
}

apiValidation {
    ignoredClasses.add("com.okta.oauth2.BuildInfo")
}

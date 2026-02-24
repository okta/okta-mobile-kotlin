plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.dokka)
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    id("com.vanniktech.maven.publish.base")
    id("spotless")
    id("binary-compatibility-validator")
    id("androidx.room")
}

// KMP androidLibrary does not generate BuildConfigs so we generate a BuildInfo.kt file instead.
val generateBuildInfoTask =
    tasks.register("generateBuildInfo") {
        description = "Generates BuildInfo.kt with the project version."
        group = "build"

        val outputDir = layout.buildDirectory.dir("generated/source/buildInfo/kotlin")
        outputs.dir(outputDir)

        doLast {
            val outputFile = outputDir.get().file("com/okta/authfoundation/BuildInfo.kt").asFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(
                """
            |package com.okta.authfoundation
            |
            |internal const val SDK_VERSION: String = "okta-auth-foundation-kotlin/$AUTH_FOUNDATION_VERSION"
                """.trimMargin()
            )
        }
    }

kotlin {
    androidLibrary {
        namespace = "com.okta.authfoundation"
        compileSdk = COMPILE_SDK
        minSdk = MIN_SDK

        // Enable Android resources processing
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true

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

    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfoTask)

            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlin.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
            }
        }

        androidMain {
            dependencies {
                api(libs.kotlin.stdlib)
                api(libs.okhttp.core)
                api(libs.coroutines.core)
                api(libs.okio.core)
                api(libs.androidx.biometric)

                implementation(libs.ktor.client.okhttp)
                implementation(libs.lifecycle.viewmodel.ktx)
                implementation(libs.androidx.activity.ktx)
                implementation(libs.androidx.constraintlayout)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidx.sqlite)
                implementation(libs.app.compat)
                implementation(libs.kotlin.serialization.okio)
                implementation(libs.security.crypto)
                implementation(libs.room.runtime)
                implementation(libs.room.ktx)
                implementation(libs.sqlcipher.android)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.coroutines.test)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.junit)
                implementation(libs.truth)
                implementation(libs.kotlin.test)
                implementation(libs.mockito.core)
                implementation(libs.mockito.kotlin)
                implementation(libs.mockk.android)
                implementation(libs.mockk.agent)
                implementation(libs.robolectric)
                implementation(libs.bcprov.jdk18on)
                implementation(libs.room.testing)
                implementation(libs.turbine)
                implementation(libs.hamcrest)
                implementation(project(":test-helpers"))
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.truth)
                implementation(libs.kotlin.test)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.rules)
                implementation(libs.coroutines.test)
            }
        }
    }
}

room {
    generateKotlin = true
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    coreLibraryDesugaring(libs.core.library.desugaring)
}

apiValidation {
    ignoredClasses.add("com.okta.authfoundation.BuildInfo")
}

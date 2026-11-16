import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.dokka)
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
            val outputFile = outputDir.get().file("com/okta/idx/kotlin/BuildInfo.kt").asFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(
                """
            |package com.okta.idx.kotlin
            |
            |internal const val SDK_VERSION: String = "okta-idx-kotlin/$IDX_KOTLIN_VERSION"
                """.trimMargin()
            )
        }
    }

kotlin {
    android {
        namespace = "com.okta.idx.kotlin"
        compileSdk = COMPILE_SDK
        minSdk = MIN_SDK

        optimization {
            consumerKeepRules.publish = true
        }

        withHostTestBuilder {
            sourceSetTreeName = "test"
        }

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
                    freeCompilerArgs.add("-opt-in=com.okta.authfoundation.InternalAuthFoundationApi")
                }
            }
        }
    }

    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
                    freeCompilerArgs.add("-opt-in=com.okta.authfoundation.InternalAuthFoundationApi")
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
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }

        androidMain {
            dependencies {
                api(libs.kotlin.stdlib)
                api(libs.okhttp.core)
                api(libs.okio.core)
                api(libs.coroutines.android)

                implementation(libs.androidx.datastore.preferences)
                implementation(libs.okio.jvm)
                implementation(libs.security.crypto)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.coroutines.jdk8)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.junit)
            }
        }

        getByName("androidHostTest") {
            resources.srcDir("src/androidHostTest/resources")
            dependencies {
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.coroutines.test)
                implementation(libs.robolectric)
                implementation(libs.bcprov.jdk18on)
                implementation(libs.json)
                implementation(project(":test-utils"))
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
                byNames.add("com.okta.idx.kotlin.BuildInfo")
            }
        }
    }
}

binaryCompatValidationExtension {
    taskNamePrefix.set("android")
    kotlinCompileTaskName.set("compileAndroidMain")
    javaCompileTaskName.set("")
    ignoredClasses.add("com.okta.idx.kotlin.BuildInfo")
}

java {
    sourceCompatibility = SOURCE_COMPATIBILITY
    targetCompatibility = TARGET_COMPATIBILITY
}

// Per FR-009: the `jvm` target is not published in this change. The root project's
// `subprojects { }` block configures every KMP module via the generic
// `KotlinMultiplatform(...)` publish preset, which by default publishes `android`, `jvm`, and a
// root `kotlinMultiplatform` metadata publication. The root publication MUST stay enabled — it is
// the bare `com.okta.kotlin:okta-idx-kotlin` coordinate existing Android consumers already
// resolve (Gradle Module Metadata redirects it to `okta-idx-kotlin-android` at resolution time,
// confirmed via an existing `auth-foundation` local-repo publish's `available-at` variants);
// disabling it would break FR-002. Only the standalone `jvm`-suffixed artifact is withheld.
afterEvaluate {
    tasks
        .matching { task ->
            task.name.startsWith("publish") && task.name.contains("JvmPublication")
        }.configureEach {
            enabled = false
        }
}

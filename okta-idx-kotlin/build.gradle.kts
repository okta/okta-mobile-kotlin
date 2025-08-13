import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("spotless")
    id("binary-compatibility-validator")
    kotlin("android")
    alias(libs.plugins.dokka)
    id("com.vanniktech.maven.publish.base")
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

android {
    namespace = "com.okta.idx.kotlin"
    compileSdk = COMPILE_SDK

    defaultConfig {
        minSdk = MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "SDK_VERSION", "\"okta-idx-kotlin/$IDX_KOTLIN_VERSION\"")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = SOURCE_COMPATIBILITY
        targetCompatibility = TARGET_COMPATIBILITY
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
            freeCompilerArgs.add("-opt-in=com.okta.authfoundation.InternalAuthFoundationApi")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

apiValidation {
    ignoredClasses.add("com.okta.idx.kotlin.BuildConfig")
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)
    api(libs.kotlin.stdlib)
    api(libs.okhttp.core)
    api(libs.okio.core)
    api(libs.coroutines.android)
    api(libs.okta.auth.foundation)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.okio.jvm)
    implementation(libs.security.crypto)

    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mock.web.server)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.okio.core)
    testImplementation(libs.okio.jvm)
    testImplementation(libs.truth)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.robolectric)
    testImplementation(libs.bcprov.jdk18on)
    testImplementation(libs.json)
    testImplementation(project(":test-utils"))
}

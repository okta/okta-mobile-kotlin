import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    id("spotless")
}

android {
    namespace = "com.okta.testing"
    compileSdk = COMPILE_SDK

    defaultConfig {
        minSdk = MIN_SDK
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
        buildConfig = false
    }

    packaging {
        resources {
            excludes += "META-INF/*"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)
    api(libs.okta.auth.foundation)
    api(libs.junit)
    api(libs.okhttp.mock.web.server)
    api(libs.okhttp.tls)
    api(libs.okio.core)
    api(libs.truth)
    api(libs.jackson.databind)
    api(libs.mockk.agent)
    api(libs.mockk.android)
}

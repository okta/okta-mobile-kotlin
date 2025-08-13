import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("binary-compatibility-validator")
    id("spotless")
    kotlin("android")
    alias(libs.plugins.dokka)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    id("com.vanniktech.maven.publish.base")
}

android {
    namespace = "com.okta.oauth2"
    compileSdk = COMPILE_SDK

    defaultConfig {
        minSdk = MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "SDK_VERSION", "\"okta-oauth2-kotlin/$OAUTH2_VERSION\"")
    }

    buildTypes {
        release {
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
    ignoredClasses.add("com.okta.oauth2.BuildConfig")
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)

    api(libs.kotlin.stdlib)
    api(libs.okhttp.core)
    api(libs.coroutines.android)
    api(libs.kotlin.serialization.json)
    api(libs.okio.core)
    api(project(":auth-foundation"))

    implementation(libs.okio.jvm)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.bcprov.jdk18on)
    testImplementation(project(":test-helpers"))
}

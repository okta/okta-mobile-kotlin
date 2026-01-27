import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    id("binary-compatibility-validator")
    id("spotless")
}

android {
    namespace = "com.okta.nativeauthentication"
    compileSdk = COMPILE_SDK

    defaultConfig {
        minSdk = MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "SDK_VERSION", "\"okta-native-authentication-kotlin/$IDX_NATIVE_AUTH_VERSION\"")
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

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
        freeCompilerArgs.add("-opt-in=com.okta.authfoundation.InternalAuthFoundationApi")
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)
    api(project(":okta-idx-kotlin"))
    api(libs.kotlin.stdlib)
    api(libs.okhttp.core)
    api(libs.okio.core)
    api(libs.coroutines.android)

    implementation(libs.kotlin.serialization.json)
    implementation(libs.okio.jvm)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.okhttp.mock.web.server)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.okio.core)
    testImplementation(libs.okio.jvm)
    testImplementation(libs.truth)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.robolectric)
    testImplementation(libs.bcprov.jdk18on)
    testImplementation(project(":test-utils"))
}

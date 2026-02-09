import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("spotless")
}

android {
    compileSdk = COMPILE_SDK

    defaultConfig {
        minSdk = MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    namespace = "com.okta.testhelpers"

    packaging {
        resources.excludes.add("META-INF/*")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = SOURCE_COMPATIBILITY
        targetCompatibility = TARGET_COMPATIBILITY
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)

    api(project(":auth-foundation"))
    api(libs.kotlin.stdlib)
    api(libs.okhttp.core)
    api(libs.okio.core)
    api(libs.junit)
    api(libs.okhttp.mock.web.server)

    implementation(libs.kotlin.serialization.json)
    implementation(libs.okio.jvm)
    implementation(libs.okhttp.tls)
    implementation(libs.mockk.android)
    implementation(libs.mockk.agent)
}

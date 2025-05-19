plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = COMPILE_SDK

    defaultConfig {
        minSdk = MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    namespace = "com.okta.testhelpers"

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes.add("META-INF/*")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = SOURCE_COMPATIBILITY
        targetCompatibility = TARGET_COMPATIBILITY
    }

    kotlinOptions {
        jvmTarget = JVM_TARGET
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
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

    implementation(libs.okio.jvm)
    implementation(libs.okhttp.tls)
    implementation(libs.mockk.android)
    implementation(libs.mockk.agent)
}
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    id("spotless")
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            rootProject.file("local.properties").inputStream().use { load(it) }
        }
    }

android {
    namespace = "com.okta.directauth.app.android"
    compileSdk { version = release(COMPILE_SDK) }

    defaultConfig {
        applicationId = "com.okta.directauth.app"
        minSdk = 28
        targetSdk = TARGET_SDK
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["webAuthenticationRedirectScheme"] =
            parseScheme(localProperties.getProperty("signInRedirectUri"))
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)
    implementation(project(":okta-direct-auth-shared"))
    implementation(project(":web-authentication-ui"))
    implementation(libs.jetbrains.compose.runtime)
    implementation(libs.jetbrains.compose.ui)
    implementation(libs.jetbrains.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.core.ktx)
}

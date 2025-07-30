import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("androidx.navigation.safeargs.kotlin")
    id("spotless")
}

val oktaProperties =
    Properties().apply {
        rootProject.file("okta.properties").inputStream().use { load(it) }
    }

android {
    namespace = "sample.okta.android.legacy"
    compileSdk = COMPILE_SDK

    defaultConfig {
        applicationId = "sample.okta.oidc.android.legacy"
        minSdk = MIN_SDK
        targetSdk = TARGET_SDK
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "ISSUER", "\"${oktaProperties.getProperty("issuer")}\"")
        buildConfigField("String", "CLIENT_ID", "\"${oktaProperties.getProperty("clientId")}\"")
        buildConfigField("String", "SIGN_IN_REDIRECT_URI", "\"${oktaProperties.getProperty("signInRedirectUri")}\"")
        buildConfigField("String", "SIGN_OUT_REDIRECT_URI", "\"${oktaProperties.getProperty("signOutRedirectUri")}\"")
        buildConfigField("String", "LEGACY_SIGN_IN_REDIRECT_URI", "\"${oktaProperties.getProperty("legacySignInRedirectUri")}\"")
        buildConfigField("String", "LEGACY_SIGN_OUT_REDIRECT_URI", "\"${oktaProperties.getProperty("legacySignOutRedirectUri")}\"")

        manifestPlaceholders["webAuthenticationRedirectScheme"] = parseScheme(oktaProperties.getProperty("signInRedirectUri"))
        manifestPlaceholders["appAuthRedirectScheme"] = parseScheme(oktaProperties.getProperty("legacySignInRedirectUri"))
    }

    sourceSets {
        getByName("androidTest") {
            java.srcDirs("src/sharedTest/java")
        }
        getByName("test") {
            java.srcDirs("src/sharedTest/java")
        }
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
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes.add("META-INF/*")
    }

    testOptions {
        animationsDisabled = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)

    implementation(project(":web-authentication-ui"))
    implementation(project(":legacy-token-migration"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.core.ktx)
    implementation(libs.app.compat)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.tls)
    implementation(libs.okio.core)
    implementation(libs.okio.jvm)
    implementation(libs.timber)

    debugImplementation(libs.leakcanary.android)
}

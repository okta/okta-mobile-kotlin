import java.util.Properties

plugins {
    id("spotless")
    id("com.android.application")
    id("kotlin-parcelize")
    kotlin("android")
    id("androidx.navigation.safeargs.kotlin")
}

val oktaProperties =
    Properties().apply {
        rootProject.file("okta.properties").inputStream().use { load(it) }
    }

android {
    namespace = "sample.okta.android"
    compileSdk = COMPILE_SDK

    defaultConfig {
        applicationId = "sample.okta.oidc.android"
        minSdk = MIN_SDK
        targetSdk = TARGET_SDK
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "ISSUER", "\"${oktaProperties.getProperty("issuer")}\"")
        buildConfigField("String", "CLIENT_ID", "\"${oktaProperties.getProperty("clientId")}\"")
        buildConfigField("String", "SIGN_IN_REDIRECT_URI", "\"${oktaProperties.getProperty("signInRedirectUri")}\"")
        buildConfigField("String", "SIGN_OUT_REDIRECT_URI", "\"${oktaProperties.getProperty("signOutRedirectUri")}\"")

        manifestPlaceholders["webAuthenticationRedirectScheme"] = parseScheme(oktaProperties.getProperty("signInRedirectUri"))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    kotlinOptions {
        jvmTarget = JVM_TARGET
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
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

    implementation(libs.kotlin.stdlib)
    implementation(libs.core.ktx)
    implementation(libs.app.compat)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.java8)
    implementation(libs.material)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.tls)
    implementation(libs.okio.core)
    implementation(libs.okio.jvm)
    implementation(libs.timber)

    debugImplementation(libs.leakcanary.android)

    androidTestUtil(libs.androidx.test.orchestrator)

    androidTestImplementation(libs.jackson.yaml)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.navigation)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib) {
        exclude(group = "org.checkerframework", module = "checker")
    }
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.jsoup)
    androidTestImplementation(libs.truth)

    androidTestImplementation(libs.okta.management.sdk) {
        exclude(group = "org.bouncycastle")
    }
}

import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("androidx.navigation.safeargs.kotlin")
}

val oktaProperties = Properties().apply {
    rootProject.file("okta.properties").inputStream().use { load(it) }
}

android {
    namespace = "sample.okta.android.sessiontoken"
    compileSdk = COMPILE_SDK

    defaultConfig {
        applicationId = "sample.okta.oidc.android.sessionToken"
        minSdk = MIN_SDK
        targetSdk = TARGET_SDK
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "ISSUER", "\"${oktaProperties.getProperty("issuer")}\"")
        buildConfigField("String", "CLIENT_ID", "\"${oktaProperties.getProperty("clientId")}\"")
        buildConfigField("String", "SIGN_IN_REDIRECT_URI", "\"${oktaProperties.getProperty("signInRedirectUri")}\"")
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
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes.add("META-INF/*")
        resources.pickFirsts.add("META-INF/okta/version.properties")
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)

    implementation(project(":oauth2"))

    implementation(libs.okta.authn.api)
    runtimeOnly(libs.okta.authn.impl) {
        exclude(group = "com.okta.sdk", module = "okta-sdk-httpclient")
        exclude(group = "org.bouncycastle")
    }
    runtimeOnly(libs.jackson.databind)
    runtimeOnly(libs.snakeyaml)

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
}

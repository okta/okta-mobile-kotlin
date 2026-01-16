import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.navigation.safeargs)
    id("spotless")
}

android {
    namespace = "com.okta.idx.android.dynamic"
    compileSdk = COMPILE_SDK

    val localProperties =
        Properties().apply {
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                rootProject.file("local.properties").inputStream().use { load(it) }
            }
        }

    defaultConfig {
        manifestPlaceholders +=
            mapOf(
                "oktaIdxRedirectScheme" to parseScheme(localProperties.getProperty("signInRedirectUri", "")),
                "oktaIdxEmailHost" to localProperties.getProperty("emailRedirectHost", ""),
                "oktaIdxEmailPrefix" to localProperties.getProperty("emailRedirectPrefix", "")
            )
        testInstrumentationRunnerArguments += mapOf()
        applicationId = "com.okta.idx.android"
        minSdk = MIN_SDK
        targetSdk = TARGET_SDK
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "ISSUER", "\"${localProperties.getProperty("issuer", "")}\"")
        buildConfigField("String", "CLIENT_ID", "\"${localProperties.getProperty("clientId", "")}\"")
        buildConfigField("String", "REDIRECT_URI", "\"${localProperties.getProperty("signInRedirectUri", "")}\"")

        testInstrumentationRunner = "io.cucumber.android.runner.CucumberAndroidJUnitRunner"
    }

    sourceSets {
        getByName("androidTest") {
            java.directories.add("src/sharedTest/java")
        }
        getByName("test") {
            java.directories.add("src/sharedTest/java")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = SOURCE_COMPATIBILITY
        targetCompatibility = TARGET_COMPATIBILITY
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            // Use a set-like syntax to add exclusions.
            excludes += "META-INF/**"
        }
    }

    testOptions {
        animationsDisabled = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)
    implementation(project(":okta-idx-kotlin"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.core.ktx)
    implementation(libs.app.compat)
    implementation(libs.androidx.constraintlayout)
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
    implementation(libs.androidx.credentials.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.jackson.databind)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mock.web.server)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.okio.core)
    testImplementation(libs.okio.jvm)
    testImplementation(libs.truth)
    testImplementation(project(":test-utils"))

    androidTestUtil(libs.androidx.test.orchestrator)

    androidTestImplementation(libs.jackson.yaml)
    androidTestImplementation(libs.okhttp.tls)
    androidTestImplementation(libs.okio.core)
    androidTestImplementation(libs.okio.jvm)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.navigation)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.jsoup)
    androidTestImplementation(libs.truth)
    androidTestImplementation(project(":test-utils"))
    androidTestImplementation(libs.cucumber.android)

    androidTestImplementation(libs.okta.management.sdk) {
        exclude(group = "org.bouncycastle")
    }
}

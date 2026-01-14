import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

android {
    namespace = "com.okta.directauth.app"
    compileSdk {
        version = release(36)
    }
    val localProperties = Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            rootProject.file("local.properties").inputStream().use { load(it) }
        }
    }

    defaultConfig {
        applicationId = "com.okta.directauth.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val issuer = localProperties.getProperty("issuer")
        val clientId = localProperties.getProperty("clientId")
        val authorizationServerId = localProperties.getProperty("authorizationServerId")
        val isCi = System.getenv("CI")?.toBoolean() ?: false

        if (!isCi && (issuer.isNullOrEmpty() || clientId.isNullOrEmpty() || authorizationServerId.isNullOrEmpty())) {
            throw GradleException(
                "Missing required properties in local.properties. Please add the following:\n" +
                    "issuer=<your_issuer>\n" +
                    "clientId=<your_client_id>\n" +
                    "authorizationServerId=<your_authorization_server_id>\n" +
                    "Direct Auth configuration: https://developer.okta.com/docs/guides/configure-direct-auth-grants"
            )
        }

        buildConfigField("String", "ISSUER", "\"${issuer ?: ""}\"")
        buildConfigField("String", "CLIENT_ID", "\"${clientId ?: ""}\"")
        buildConfigField("String", "AUTHORIZATION_SERVER_ID", "\"${authorizationServerId ?: ""}\"")
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)
    implementation(project(":okta-direct-auth"))
    implementation(project(":auth-foundation"))
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

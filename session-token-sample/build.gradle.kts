import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("androidx.navigation.safeargs.kotlin")
    id("spotless")
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun local(key: String): String = localProperties.getProperty(key) ?: ""

android {
    namespace = "sample.okta.android.sessiontoken"
    compileSdk { version = release(COMPILE_SDK) }

    defaultConfig {
        applicationId = "sample.okta.oidc.android.sessionToken"
        minSdk = MIN_SDK
        targetSdk = TARGET_SDK
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "ISSUER", "\"${local("issuer")}\"")
        buildConfigField("String", "CLIENT_ID", "\"${local("clientId")}\"")
        buildConfigField("String", "SIGN_IN_REDIRECT_URI", "\"${local("signInRedirectUri")}\"")
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes.add("META-INF/*")
        resources.pickFirsts.add("META-INF/okta/version.properties")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(JVM_TARGET)
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
    implementation(libs.material)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.tls)
    implementation(libs.okio.core)
    implementation(libs.okio.jvm)
    implementation(libs.timber)
    implementation(libs.kotlin.serialization.json)

    debugImplementation(libs.leakcanary.android)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("org.jetbrains.dokka")
    id("spotless")
    kotlin("android")
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    id("com.vanniktech.maven.publish.base")
    id("binary-compatibility-validator")
    id("androidx.room")
}

android {
    namespace = "com.okta.authfoundation"
    compileSdk = COMPILE_SDK

    room {
        generateKotlin = true
        schemaDirectory("$projectDir/schemas")
    }

    defaultConfig {
        minSdk = MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "SDK_VERSION", "\"okta-auth-foundation-kotlin/$AUTH_FOUNDATION_VERSION\"")
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
            freeCompilerArgs.addAll(listOf("-opt-in=kotlin.RequiresOptIn", "-opt-in=com.okta.authfoundation.InternalAuthFoundationApi"))
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

apiValidation {
    ignoredClasses.add("com.okta.authfoundation.BuildConfig")
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)

    api(libs.kotlin.stdlib)
    api(libs.okhttp.core)
    api(libs.coroutines.core)
    api(libs.kotlin.serialization.json)
    api(libs.okio.core)
    api(libs.androidx.biometric)

    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.sqlite)
    implementation(libs.app.compat)
    implementation(libs.okio.jvm)
    implementation(libs.kotlin.serialization.okio)
    implementation(libs.security.crypto)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.sqlcipher.android)
    annotationProcessor(libs.room.compiler)
    ksp(libs.room.compiler)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockk.android)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.robolectric)
    testImplementation(libs.bcprov.jdk18on)
    testImplementation(libs.room.testing)
    testImplementation(libs.turbine)
    testImplementation(project(":test-helpers"))

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.coroutines.test)
}

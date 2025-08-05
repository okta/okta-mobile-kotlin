import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.dokka")
    id("binary-compatibility-validator")
    id("spotless")
    kotlin("android")
    id("com.vanniktech.maven.publish.base")
}

android {
    namespace = "com.okta.legacytokenmigration"
    compileSdk = COMPILE_SDK

    defaultConfig {
        minSdk = MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    testVariants.all {
        mergedFlavor.manifestPlaceholders["appAuthRedirectScheme"] = "unitTest"
    }

    unitTestVariants.all {
        mergedFlavor.manifestPlaceholders["appAuthRedirectScheme"] = "unitTest"
    }
}

apiValidation {
    ignoredClasses.add("com.okta.legacytokenmigration.BuildConfig")
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)

    api(libs.okta.legacy.oidc)
    api(project(":auth-foundation"))
    api(libs.kotlin.stdlib)
    api(libs.coroutines.core)

    implementation(libs.androidx.annotation)

    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk.android)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.robolectric)
    testImplementation(libs.bcprov.jdk18on)
    testImplementation(libs.truth)
    testImplementation(project(":test-helpers"))
}

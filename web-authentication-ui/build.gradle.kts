plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("org.jetbrains.dokka")
    id("binary-compatibility-validator")
    kotlin("android")
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    id("com.vanniktech.maven.publish.base")
}

android {
    namespace = "com.okta.webauthenticationui"
    compileSdk = COMPILE_SDK

    defaultConfig {
        minSdk = MIN_SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "SDK_VERSION", "\"okta-web-authentication-ui-kotlin/$WEB_AUTHENTICATION_UI_VERSION\"")
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
        freeCompilerArgs += listOf("-Xopt-in=com.okta.authfoundation.InternalAuthFoundationApi")
    }

    buildFeatures {
        buildConfig = true
    }

    testVariants.all {
        mergedFlavor.manifestPlaceholders["webAuthenticationRedirectScheme"] = "unitTest"
    }

    unitTestVariants.all {
        mergedFlavor.manifestPlaceholders["webAuthenticationRedirectScheme"] = "unitTest"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

apiValidation {
    ignoredClasses.add("com.okta.webauthenticationui.BuildConfig")
}

dependencies {
    coreLibraryDesugaring(libs.core.library.desugaring)

    api(libs.kotlin.stdlib)
    api(libs.okhttp.core)
    api(libs.okio.core)
    api(libs.coroutines.android)
    api(libs.kotlin.serialization.json)
    api(project(":auth-foundation"))
    api(project(":oauth2"))
    api(libs.androidx.browser)

    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.app.compat)
    implementation(libs.okio.jvm)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.bcprov.jdk18on)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockk.android)
    testImplementation(libs.mockk.agent)
    testImplementation(project(":test-helpers"))
}

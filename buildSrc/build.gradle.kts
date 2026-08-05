plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `kotlin-dsl-precompiled-script-plugins`
}

repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.9.0")
    implementation("org.owasp:dependency-check-gradle:12.2.0")
    // Pinned exactly to the version applied elsewhere in the project (gradle/libs.versions.toml).
    // BinaryCompatValidationPlugin depends on kotlinx.validation.KotlinApiBuildTask/KotlinApiCompareTask,
    // which are BCV-internal types, not public API - bumping this requires re-verifying that plugin.
    //noinspection UseTomlInstead
    implementation("org.jetbrains.kotlinx:binary-compatibility-validator:0.18.1")
}

gradlePlugin {
    plugins {
        create("binaryCompatValidation") {
            id = "binary-compat-validation"
            implementationClass = "BinaryCompatValidationPlugin"
        }
    }
}

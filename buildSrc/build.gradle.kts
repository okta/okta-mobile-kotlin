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
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.2.1")
    implementation("org.owasp:dependency-check-gradle:12.2.0")
}

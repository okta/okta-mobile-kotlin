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
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.3")
    implementation("org.owasp:dependency-check-gradle:10.0.3")
}

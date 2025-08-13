import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import java.net.URI

buildscript {
    configurations.all {
        resolutionStrategy {
            force(libs.bcprov.jdk18on)
            force(libs.bcpkix.jdk18on)
            force(libs.bcutil.jdk18on)
        }
    }

    dependencies {
        classpath(libs.gradle.maven.publish)
        classpath(libs.bcprov.jdk18on)
        classpath(libs.bcpkix.jdk18on)
        classpath(libs.bcutil.jdk18on)
    }
}

plugins {
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.dokka).apply(false)
    alias(libs.plugins.sonarqube).apply(true)
    alias(libs.plugins.kover).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.androidx.navigation.safeargs).apply(false)
    alias(libs.plugins.kotlinx.binary.compatibility.validator).apply(false)
    alias(libs.plugins.androidx.room).apply(false)
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force(libs.jackson.databind)
            force(libs.bcprov.jdk18on)
            force(libs.commons.io)
            force(libs.netty.common)
            force(libs.netty.codec.http)
            force(libs.netty.codec.http2)
            force(libs.netty.handler)
            force(libs.protobuf.java)
            force(libs.woodstox.core)
        }
    }

    configurations.matching { it.name.startsWith("dokka") }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group.startsWith("com.fasterxml.jackson")) {
                useVersion("2.15.3")
            }
        }
    }
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish.base") {
        configure<MavenPublishBaseExtension> {
            val snapshot = project.properties["snapshot"]?.toString()?.toBoolean() ?: false
            val automaticRelease = if (snapshot) false else project.properties["automaticRelease"]?.toString()?.toBoolean() ?: false

            publishToMavenCentral(automaticRelease)
            if (project.hasProperty("signAllPublications")) signAllPublications()

            pom {
                name.set(pomName(project))
                description.set("Okta Mobile Kotlin")
                url.set("https://github.com/okta/okta-mobile-kotlin")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("alexnachbaur-okta")
                        name.set("Alex Nachbaur")
                        email.set("alex.nachbaur@okta.com")
                    }
                    developer {
                        id.set("rajdeepnanua-okta")
                        name.set("Rajdeep Nanua")
                        email.set("rajdeep.nanua@okta.com")
                    }
                    developer {
                        id.set("FeiChen-okta")
                        name.set("Fei Chen")
                        email.set("fei.chen@okta.com")
                    }
                }
                scm {
                    connection.set("scm:git@github.com:okta/okta-mobile-kotlin.git")
                    developerConnection.set("scm:git@github.com:okta/okta-mobile-kotlin.git")
                    url.set("https://github.com/okta/okta-mobile-kotlin.git")
                }
            }
            group = "com.okta.kotlin"
            version = releaseVersion(project).let { if (snapshot) "$it-SNAPSHOT" else it }

            if (plugins.hasPlugin("com.android.library")) {
                configure(com.vanniktech.maven.publish.AndroidSingleVariantLibrary("release"))
            }
        }
    }
}

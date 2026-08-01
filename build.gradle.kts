import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar

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
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.sonarqube) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.navigation.safeargs) apply false
    // No apply-false declaration here: buildSrc's own dependency on this artifact (for
    // AndroidBcvBridgePlugin) already puts it on the classpath for every subproject.
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.jetbrains.compose) apply false
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
            force(libs.netty.handler.proxy)
            force(libs.protobuf.java)
            force(libs.woodstox.core)
            force(libs.commons.lang3)
            force(libs.bcprov.jdk18on)
            force(libs.bcpkix.jdk18on)
            force(libs.bcutil.jdk18on)
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

tasks.register("checkLegacyAbi") {
    description = "Checks API binary compatibility across all modules."
    group = "verification"
}

gradle.projectsEvaluated {
    tasks.named("checkLegacyAbi").configure {
        subprojects.forEach { subproject ->
            // checkKotlinAbi: KGP's built-in ABI validation (auth-foundation/oauth2's jvm target).
            // androidApiCheck: binary-compat-validation, for KMP android targets KGP's validator can't see.
            // releaseApiCheck: binary-compat-validation, for Android-only modules on AGP built-in Kotlin.
            listOfNotNull(
                subproject.tasks.findByName("checkKotlinAbi"),
                subproject.tasks.findByName("androidApiCheck"),
                subproject.tasks.findByName("releaseApiCheck")
            ).forEach { dependsOn(it) }
        }
    }
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish.base") {
        configure<MavenPublishBaseExtension> {
            val snapshot = project.findProperty("snapshot")?.toString()?.toBoolean() ?: false
            val automaticRelease = if (snapshot) false else project.findProperty("automaticRelease")?.toString()?.toBoolean() ?: false

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

            when {
                plugins.hasPlugin("com.android.library") -> {
                    configure(
                        AndroidSingleVariantLibrary(
                            variant = "release",
                            sourcesJar = SourcesJar.Sources(),
                            javadocJar = JavadocJar.Dokka("dokkaGenerateModuleHtml")
                        )
                    )
                }

                plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") -> {
                    configure(
                        KotlinMultiplatform(
                            javadocJar = JavadocJar.Dokka("dokkaGenerateModuleHtml"),
                            sourcesJar = SourcesJar.Sources()
                        )
                    )
                }
            }
        }
    }
}

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import java.net.URI
import kotlin.math.sign

buildscript {
    dependencies {
        classpath(libs.gradle.maven.publish)
    }
}

plugins {
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.dokka).apply(true)
    alias(libs.plugins.kover).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.androidx.navigation.safeargs).apply(false)
    alias(libs.plugins.kotlinx.binary.compatibility.validator).apply(false)
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force(libs.jackson.databind)
        }
    }
}

subprojects {
    tasks.withType<DokkaTaskPartial>().configureEach {
        dokkaSourceSets.configureEach {
            jdkVersion.set(11)
            suppressInheritedMembers.set(true)

            perPackageOption {
                matchingRegex.set(".*\\.internal.*")
                suppress.set(true)
            }
            if (project.file("Module.md").exists()) {
                includes.from(project.file("Module.md"))
            }
            externalDocumentationLink {
                url.set(URI.create("https://square.github.io/okio/3.x/okio/").toURL())
                packageListUrl.set(URI.create("https://square.github.io/okio/3.x/okio/okio/package-list").toURL())
            }
            externalDocumentationLink {
                url.set(URI.create("https://kotlin.github.io/kotlinx.serialization/").toURL())
                packageListUrl.set(URI.create("https://kotlin.github.io/kotlinx.serialization/package-list").toURL())
            }
        }
    }

    plugins.withId("com.vanniktech.maven.publish.base") {
        configure<MavenPublishBaseExtension> {

            publishToMavenCentral(SonatypeHost.DEFAULT)
            if (project.hasProperty("signAllPublications")) {
                signAllPublications()
            }
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
            version = releaseVersion(project)

            if (plugins.hasPlugin("com.android.library")) {
                configure(com.vanniktech.maven.publish.AndroidSingleVariantLibrary("release"))
            }
        }
    }
}

tasks.dokkaHtmlMultiModule.configure {
    outputDirectory.set(file("${rootDir}/docs"))
}

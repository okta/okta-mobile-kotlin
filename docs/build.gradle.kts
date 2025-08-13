plugins {
    `java-library`
    alias(libs.plugins.dokka)
}

dependencies {
    dokkaPlugin(libs.android.documentation.plugin)
    dokka(project(":auth-foundation"))
    dokka(project(":legacy-token-migration"))
    dokka(project(":oauth2"))
    dokka(project(":okta-idx-kotlin"))
    dokka(project(":web-authentication-ui"))
}

dokka {
    moduleName.set("Okta Mobile Kotlin SDK")

    dokkaPublications.configureEach {
        suppressInheritedMembers.set(true)
    }

    dokkaSourceSets.configureEach {

        sourceLink {
            remoteUrl("https://github.com/okta/okta-mobile-kotlin/tree/master/okta-idx-kotlin/src")
            remoteLineSuffix.set("#L")
        }

        perPackageOption {
            matchingRegex.set(".*\\.internal.*")
            suppress.set(true)
        }

        externalDocumentationLinks.register("okio") {
            url("https://square.github.io/okio/3.x/okio/")
            packageListUrl("https://square.github.io/okio/3.x/okio/okio/package-list")
        }

        externalDocumentationLinks.register("kotlinx.serialization") {
            url("https://kotlin.github.io/kotlinx.serialization/")
            packageListUrl("https://kotlin.github.io/kotlinx.serialization/package-list")
        }
    }
}

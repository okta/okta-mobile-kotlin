pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
    }
}
rootProject.name = "okta-mobile-kotlin"

include(":docs")
include(":app")
include(":auth-foundation")
include(":bom")
include(":legacy-token-migration")
include(":legacy-token-migration-sample")
include(":oauth2")
include(":session-token-sample")
include(":suppress-internal-dokka-plugin")
include(":test-helpers")
include(":web-authentication-ui")
include(":dynamic-app")
include(":okta-idx-kotlin")
include(":native-authentication")
include(":test-utils")

include(":okta-direct-auth")
include(":okta-direct-auth-app")

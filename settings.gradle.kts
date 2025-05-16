pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
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



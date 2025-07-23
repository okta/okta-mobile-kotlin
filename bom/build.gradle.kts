import Modules.AUTH_FOUNDATION
import Modules.IDX_KOTLIN
import Modules.LEGACY_TOKEN_MIGRATION
import Modules.OAUTH2
import Modules.WEB_AUTHENTICATION_UI

plugins {
    id("com.vanniktech.maven.publish.base")
    `java-platform`
}

dependencies {
    constraints {
        project.rootProject.subprojects.forEach { subproject ->
            when (subproject.name) {
                AUTH_FOUNDATION.moduleName, WEB_AUTHENTICATION_UI.moduleName, OAUTH2.moduleName, LEGACY_TOKEN_MIGRATION.moduleName, IDX_KOTLIN.moduleName -> api(subproject)
            }
        }
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(project.components["javaPlatform"])
    }
}
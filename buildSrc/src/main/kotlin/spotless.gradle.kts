import com.diffplug.spotless.kotlin.KotlinConstants

plugins {
    id("com.diffplug.spotless")
}

spotless {
    format("misc") {
        target("**/*.gradle", "**/*.md", "**/.gitignore", "**/*.json", "**/*.properties", "**/*.yml")
        targetExclude(".idea/*", "**/build/*", ".gradle/*")
        trimTrailingWhitespace()
        endWithNewline()
    }

    java {
        target("**/*.java")
        licenseHeaderFile("${rootDir}/config/license")
        googleJavaFormat().aosp()
        endWithNewline()
    }

    kotlin {
        target("**/*.kt")
        ktlint("1.5.0")
        endWithNewline()
    }

    kotlinGradle {
        // same as kotlin, but for .gradle.kts files (defaults to "*.gradle.kts")
        target("*.gradle.kts", "additionalScripts/*.gradle.kts", "buildSrc/*.gradle.kts")
        ktlint("1.5.0")
        endWithNewline()
    }

    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**/*.xml", "**/src/main/**/*.xml", "**/src/debug/**/*.xml", "**/scripts/.venv/**/*.xml")
        licenseHeaderFile("${rootDir}/config/license.xml", "(<[^!?])")
    }
    
    format("license") {
        licenseHeaderFile("${rootDir}/config/license", KotlinConstants.LICENSE_HEADER_DELIMITER)
        target("**/*.kt")
    }
}

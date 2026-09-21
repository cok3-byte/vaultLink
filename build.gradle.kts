import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.changelog)
}

group = "dev.vaultlink"
version = "1.2.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    intellijPlatform {
        create("IC", "2025.1")
        bundledPlugin("com.intellij.java") // CommonJavaRunConfigurationParameters
        pluginVerifier()
    }
    // No vault-java-driver: native java.net.http.HttpClient avoids bundled dependencies
}

changelog {
    path.set(file("CHANGELOG.md").canonicalPath)
    version.set(project.version.toString())
    // header/groups/itemPrefix all already match this project's CHANGELOG.md — no overrides needed.
}

intellijPlatform {
    pluginConfiguration {
        id = "dev.vaultlink"
        name = "VaultLink"
        version = project.version.toString()
        // Renders the CHANGELOG.md section for this exact version as HTML for the Marketplace page
        // and the Plugin Manager's "What's new" panel. Falls back to [Unreleased] for an ad-hoc local
        // build whose version doesn't have a matching section yet (the real release flow always adds
        // the section and bumps `version` in the same `chore(release)` commit, so this never triggers
        // for a tagged release).
        changeNotes = provider {
            changelog.renderItem(
                changelog.getOrNull(project.version.toString()) ?: changelog.getUnreleased(),
                Changelog.OutputType.HTML,
            )
        }
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "262.*"
        }
    }
    pluginVerification {
        ides {
            // Explicit endpoints of the declared range (251-262.*) instead of recommended():
            // this version of the Verifier's "recommended" set tries to resolve a 2025.3 build
            // that doesn't exist (that release cycle jumped straight from 2025.2 to 2026.1).
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1") // compile baseline (251)
            ide(IntelliJPlatformType.IntellijIdeaUltimate, "2026.2")  // upper bound (262.*)
        }
    }
}

kotlin {
    jvmToolchain(21)
}

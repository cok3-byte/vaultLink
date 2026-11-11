import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.changelog)
}

group = "dev.vaultlink"
version = "1.4.0"

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
            untilBuild = "263.*"
        }
    }
    pluginVerification {
        ides {
            // Explicit endpoints of the declared range (251-263.*) instead of recommended():
            // this version of the Verifier's "recommended" set tries to resolve a 2025.3 build
            // that doesn't exist (that release cycle jumped straight from 2025.2 to 2026.1).
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1") // compile baseline (251)
            ide(IntelliJPlatformType.IntellijIdeaUltimate, "2026.3")  // upper bound (263.*)
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// Design-system guard (see docs/design-system.md): UI code must read colors, fonts and spacing from
// dev.vaultlink.ui.design instead of hardcoding them. Runs as part of `check`.
val checkDesignTokens by tasks.registering {
    group = "verification"
    description = "Fails if UI code hardcodes colors, fonts or unscaled gaps instead of using design tokens."
    val uiSources = fileTree("src/main/kotlin") {
        include("**/ui/**/*.kt")
        exclude("**/ui/design/**")
    }
    inputs.files(uiSources)
    doLast {
        val rules = mapOf(
            Regex("""JBColor\(\s*0x|JBColor\.(GRAY|GREEN|RED|BLUE|YELLOW|ORANGE|BLACK|WHITE)\b|Color\(\s*0x|#[0-9A-Fa-f]{6}\b""") to
                "hardcoded color - use VaultColors",
            Regex("""Font\(\s*Font\.|deriveFont\(""") to "hand-built font - use VaultTypography",
            Regex("""(FlowLayout|BorderLayout)\([^)]*\b[1-9]\d*\s*,\s*\d+\)""") to
                "unscaled layout gap - use VaultSpacing.px(...)",
            Regex("""(Dimension|createVerticalStrut|createHorizontalStrut)\(\s*[1-9]""") to
                "unscaled size - use JBUI.size/JBUI.scale with a token",
        )
        val violations = uiSources.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                rules.entries.firstOrNull { it.key.containsMatchIn(line) }
                    ?.let { "${file.relativeTo(projectDir)}:${i + 1}: ${it.value}\n    ${line.trim()}" }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Design token violations:\n" + violations.joinToString("\n"))
        }
    }
}
tasks.named("check") { dependsOn(checkDesignTokens) }

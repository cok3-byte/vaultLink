import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij.platform)
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

intellijPlatform {
    pluginConfiguration {
        id = "dev.vaultlink"
        name = "VaultLink"
        version = project.version.toString()
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

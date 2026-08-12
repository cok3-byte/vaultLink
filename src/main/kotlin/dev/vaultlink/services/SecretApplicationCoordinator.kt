package dev.vaultlink.services

import com.intellij.openapi.project.Project
import dev.vaultlink.core.vault.model.VaultSecretData
import dev.vaultlink.integration.runconfig.DotEnvFileWriter
import java.io.File

/** Decides which destination(s) to apply the secret to: JVM Run Config (live, via the extension) and/or .env. */
class SecretApplicationCoordinator(private val project: Project) {

    fun apply(secret: VaultSecretData, strategy: EnvApplyStrategy, hasJvmRunConfig: Boolean) {
        val useDotEnv = when (strategy) {
            EnvApplyStrategy.DOTENV_ONLY -> true
            EnvApplyStrategy.RUN_CONFIG_ONLY -> false
            EnvApplyStrategy.AUTO -> !hasJvmRunConfig
        }
        if (useDotEnv) {
            val projectRoot = project.basePath?.let(::File) ?: return
            DotEnvFileWriter.write(projectRoot, secret.data)
        }
        // The JVM Run Config case is applied live by VaultEnvRunConfigurationExtension.updateJavaParameters,
        // not this coordinator — here we only decide whether .env is also needed.
    }
}

package dev.vaultlink.integration.runconfig

import com.intellij.execution.CommonJavaRunConfigurationParameters
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import dev.vaultlink.services.EnvApplyStrategy
import dev.vaultlink.services.VaultProjectService
import dev.vaultlink.services.VaultProjectSettingsService

/**
 * Injects the secret's variables into Run/Debug at runtime (updateJavaParameters) — never
 * written to the run configuration's persisted XML.
 */
class VaultEnvRunConfigurationExtension : RunConfigurationExtension() {

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean =
        configuration is CommonJavaRunConfigurationParameters

    override fun isEnabledFor(applicableConfiguration: RunConfigurationBase<*>, runnerSettings: RunnerSettings?): Boolean = true

    override fun <T : RunConfigurationBase<*>> updateJavaParameters(
        configuration: T,
        params: JavaParameters,
        runnerSettings: RunnerSettings?,
    ) {
        if (configuration !is CommonJavaRunConfigurationParameters) return
        val service = configuration.project.getService(VaultProjectService::class.java)
        if (service.effectiveApplyStrategy() == EnvApplyStrategy.DOTENV_ONLY) return

        val target = VaultProjectSettingsService.getInstance(configuration.project).state.targetRunConfigurationName
        if (target != null && target != configuration.name) return

        val env = resolveEnv(service) ?: return
        JavaEnvApplier().apply { bind(params) }.apply(env)
    }

    private fun resolveEnv(service: VaultProjectService): Map<String, String>? {
        val secretPath = service.resolveSecretPath() ?: return null
        return runCatching { service.effectiveSecretData(secretPath) }.getOrNull()
    }
}

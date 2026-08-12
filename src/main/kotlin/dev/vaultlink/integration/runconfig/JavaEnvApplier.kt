package dev.vaultlink.integration.runconfig

import com.intellij.execution.configurations.JavaParameters

/**
 * Writes directly into JavaParameters.env — the map actually used to launch the process — never
 * CommonJavaRunConfigurationParameters.envs (the run configuration's own saved map). By the time
 * updateJavaParameters() runs, `params` has usually already copied its environment from
 * `configuration.envs`, so mutating the configuration afterward has no effect on the launched
 * process. Mutating only `params` also avoids any chance of the secret ending up persisted to
 * the run configuration's XML if it's later reopened and saved.
 */
class JavaEnvApplier : EnvApplierStrategy {
    private var target: JavaParameters? = null

    fun bind(target: JavaParameters) {
        this.target = target
    }

    override fun apply(env: Map<String, String>) {
        target?.env?.putAll(env)
    }
}

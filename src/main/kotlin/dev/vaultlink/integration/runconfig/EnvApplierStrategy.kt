package dev.vaultlink.integration.runconfig

interface EnvApplierStrategy {
    fun apply(env: Map<String, String>)
}

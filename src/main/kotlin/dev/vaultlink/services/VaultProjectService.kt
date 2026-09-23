package dev.vaultlink.services

import com.intellij.execution.CommonJavaRunConfigurationParameters
import com.intellij.execution.RunManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.vaultlink.core.auth.AuthResult
import dev.vaultlink.core.auth.AuthStrategyFactory
import dev.vaultlink.core.auth.TokenLifecycleManager
import dev.vaultlink.core.auth.VaultAuthStrategy
import dev.vaultlink.core.cache.InMemorySecretCache
import dev.vaultlink.core.credentials.VaultCredentialsStore
import dev.vaultlink.core.credentials.VaultSessionStatus
import dev.vaultlink.core.net.CustomTlsSocketFactory
import dev.vaultlink.core.net.RetryPolicy
import dev.vaultlink.core.vault.EffectiveSecret
import dev.vaultlink.core.vault.SecretPathResolver
import dev.vaultlink.core.vault.VaultClient
import dev.vaultlink.core.vault.VaultClientImpl
import dev.vaultlink.core.vault.model.SecretOverrides
import dev.vaultlink.core.vault.model.SecretPath
import dev.vaultlink.core.vault.model.VaultMount
import dev.vaultlink.core.vault.model.VaultSecretData
import dev.vaultlink.core.vault.model.VaultSecretMetadata
import java.net.http.HttpClient

/**
 * Per-project orchestrator: resolves the mount/secret from the name, authenticates (owns the
 * cache), fetches the secret, and coordinates applying it.
 *
 * NOTE: `fetchSecret`/`buildVaultClient` are intentionally synchronous — it's the caller's job
 * to run this off the EDT. `VaultToolWindowPanel` already does this inside a
 * `Task.Backgroundable`; `VaultEnvRunConfigurationExtension` runs during run-profile
 * construction (not on the EDT). The login dialogs (`CredentialsLoginDialog`, `TokenLoginDialog`)
 * are safe to call from any thread: they marshal their UI to the EDT internally.
 */
@Service(Service.Level.PROJECT)
class VaultProjectService(private val project: Project) {

    private val settings get() = VaultApplicationSettingsService.getInstance().state
    private val projectSettings get() = VaultProjectSettingsService.getInstance(project).state
    private val overridesService get() = SecretOverridesService.getInstance(project)
    private val cache by lazy { InMemorySecretCache(settings.cacheTtlMinutes) }
    private var lifecycleManager: TokenLifecycleManager? = null

    /**
     * MANUAL mode uses the mount/secret picked via [MountSecretPickerDialog] (stored in
     * [VaultProjectSettingsState]); AUTO mode (default) parses `<mount>.<secret>` from the
     * project name, same as before.
     */
    fun resolveSecretPath(): SecretPath? {
        val ps = projectSettings
        return if (ps.resolutionMode == SecretResolutionMode.MANUAL) {
            val mount = ps.manualMount
            val secret = ps.manualSecretName
            if (!mount.isNullOrBlank() && !secret.isNullOrBlank()) SecretPath(mount, secret) else null
        } else {
            SecretPathResolver.resolve(project.name)
        }
    }

    /** Lists KV v2 mounts available on the configured Vault server, for the manual picker. */
    fun listMounts(): List<VaultMount> = buildVaultClient().listMounts()

    /** Direct children of [path] within [mount] ("" = mount root), for the manual picker. */
    fun listSecrets(mount: String, path: String = ""): List<String> = buildVaultClient().listSecrets(mount, path)

    fun setResolutionMode(mode: SecretResolutionMode) {
        projectSettings.resolutionMode = mode
    }

    /** Persists a manual mount/secret selection and switches [resolveSecretPath] to MANUAL mode. */
    fun setManualSelection(mount: String, secretName: String) {
        projectSettings.resolutionMode = SecretResolutionMode.MANUAL
        projectSettings.manualMount = mount
        projectSettings.manualSecretName = secretName
    }

    /**
     * [version] explicitly overrides for this one call; otherwise falls back to the project's
     * pinned version ([VaultProjectSettingsService], set via the version picker) and finally to
     * "latest" (null) if nothing is pinned — this is the default for every caller that doesn't
     * pass a version explicitly.
     */
    fun fetchSecret(secretPath: SecretPath, version: Int? = null): VaultSecretData {
        val effectiveVersion = version ?: projectSettings.pinnedSecretVersion
        val cacheKey = "${secretPath.mount}.${secretPath.secretName}@${effectiveVersion ?: "latest"}"
        cache.get(cacheKey)?.let { return it }

        val client = buildVaultClient()
        val data = client.readSecret(secretPath.mount, secretPath.secretName, effectiveVersion)
        cache.put(cacheKey, data)
        return data
    }

    /** Lists available versions so the UI can offer a picker. */
    fun fetchVersions(secretPath: SecretPath): VaultSecretMetadata {
        val client = buildVaultClient()
        return client.readMetadata(secretPath.mount, secretPath.secretName)
    }

    /** The user's disabled-keys/edited-values adjustments for [secretPath], empty if none were made. */
    fun overridesFor(secretPath: SecretPath): SecretOverrides = overridesService.get(secretPath)

    fun setOverrides(secretPath: SecretPath, overrides: SecretOverrides) {
        overridesService.set(secretPath, overrides)
    }

    /** What actually gets injected: [fetchSecret]'s raw data with [overridesFor] applied on top. */
    fun effectiveSecretData(secretPath: SecretPath, version: Int? = null): Map<String, String> =
        EffectiveSecret.effective(fetchSecret(secretPath, version).data, overridesFor(secretPath))

    /** null pins back to "latest" (the default). */
    fun setPinnedVersion(version: Int?) {
        projectSettings.pinnedSecretVersion = version
    }

    fun pinnedVersion(): Int? = projectSettings.pinnedSecretVersion

    /** The project's own override wins; otherwise falls back to the global IDE setting. */
    fun effectiveApplyStrategy(): EnvApplyStrategy =
        projectSettings.envApplyStrategyOverride ?: settings.envApplyStrategy

    /** Whether the project has at least one JVM-compatible Run Configuration, for AUTO's real decision. */
    fun hasJvmRunConfiguration(): Boolean =
        RunManager.getInstance(project).allSettings
            .any { it.configuration is CommonJavaRunConfigurationParameters }

    /** Forces a fresh login (even if a session is already cached) and stores it, for an explicit "Login" action. */
    fun login(): AuthResult {
        val httpClient = HttpClient.newBuilder()
            .sslContext(CustomTlsSocketFactory.buildSslContext(settings.customCaCertPath))
            .build()
        return authenticateAndStore(httpClient)
    }

    /** Clears the session and any secrets cached under it, for an explicit "Logout" action. */
    fun logout() {
        lifecycleManager?.stop()
        lifecycleManager = null
        VaultCredentialsStore.clear()
        VaultSessionStatus.clear()
        cache.invalidateAll()
        // Edited values are secret material tied to this session; disabled-key exclusions are just
        // names, so they're kept — no reason to make the user redo that on every logout.
        overridesService.discardValues()
    }

    private fun buildVaultClient(): VaultClient {
        val httpClient = HttpClient.newBuilder()
            .sslContext(CustomTlsSocketFactory.buildSslContext(settings.customCaCertPath))
            .build()

        val resolvedToken = VaultCredentialsStore.read() ?: authenticateAndStore(httpClient).clientToken

        return VaultClientImpl(
            vaultUrl = settings.vaultUrl,
            namespace = settings.namespace,
            httpClient = httpClient,
            retryPolicy = RetryPolicy(),
            tokenProvider = { resolvedToken },
        )
    }

    private fun authenticateAndStore(httpClient: HttpClient): AuthResult {
        val strategy = buildAuthStrategy(httpClient)
        val auth = strategy.authenticate()
        VaultCredentialsStore.store(auth.clientToken)
        VaultSessionStatus.update(settings.authMethod, auth)
        startLifecycle(strategy, auth)
        return auth
    }

    private fun buildAuthStrategy(httpClient: HttpClient): VaultAuthStrategy = AuthStrategyFactory.create(
        authMethod = settings.authMethod,
        vaultUrl = settings.vaultUrl,
        oidcMountPath = settings.oidcMountPath,
        oidcRole = settings.oidcRole,
        callbackPort = settings.callbackPort,
        callbackPath = settings.callbackPath,
        ldapMountPath = settings.ldapMountPath,
        userpassMountPath = settings.userpassMountPath,
        httpClient = httpClient,
    )

    private fun startLifecycle(strategy: VaultAuthStrategy, auth: AuthResult) {
        lifecycleManager?.stop()
        lifecycleManager = TokenLifecycleManager(
            strategy = strategy,
            onRenewed = {
                VaultCredentialsStore.store(it.clientToken)
                VaultSessionStatus.update(strategy.method, it)
            },
            onRenewalFailed = {
                VaultCredentialsStore.clear()
                VaultSessionStatus.clear()
            },
        ).apply { start(auth) }
    }

    fun disposeProject() {
        lifecycleManager?.stop()
        cache.invalidateAll()
    }
}

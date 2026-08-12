package dev.vaultlink.services

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
import dev.vaultlink.core.vault.SecretPathResolver
import dev.vaultlink.core.vault.VaultClient
import dev.vaultlink.core.vault.VaultClientImpl
import dev.vaultlink.core.vault.model.SecretPath
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
    private val cache by lazy { InMemorySecretCache(settings.cacheTtlMinutes) }
    private var lifecycleManager: TokenLifecycleManager? = null

    fun resolveSecretPath(): SecretPath? = SecretPathResolver.resolve(project.name)

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

    /** null pins back to "latest" (the default). */
    fun setPinnedVersion(version: Int?) {
        projectSettings.pinnedSecretVersion = version
    }

    fun pinnedVersion(): Int? = projectSettings.pinnedSecretVersion

    private fun buildVaultClient(): VaultClient {
        val httpClient = HttpClient.newBuilder()
            .sslContext(CustomTlsSocketFactory.buildSslContext(settings.customCaCertPath))
            .build()

        var clientToken = VaultCredentialsStore.read()
        if (clientToken == null) {
            val strategy = buildAuthStrategy(httpClient)
            val auth = strategy.authenticate()
            VaultCredentialsStore.store(auth.clientToken)
            VaultSessionStatus.update(settings.authMethod, auth)
            startLifecycle(strategy, auth)
            clientToken = auth.clientToken
        }
        val resolvedToken = clientToken

        return VaultClientImpl(
            vaultUrl = settings.vaultUrl,
            namespace = settings.namespace,
            httpClient = httpClient,
            retryPolicy = RetryPolicy(),
            tokenProvider = { resolvedToken },
        )
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

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
     * The Vault profile this project talks to: its explicit association if it still exists,
     * otherwise the default profile. Read it once per operation and pass it along, so a profile
     * switch mid-flight can't mix one server's token with another's URL.
     */
    fun activeProfile(): VaultProfile =
        VaultApplicationSettingsService.getInstance().profile(projectSettings.profileId)

    /** The explicit association, or null when the project follows the default profile. */
    fun associatedProfileId(): String? =
        projectSettings.profileId?.takeIf { VaultApplicationSettingsService.getInstance().findProfile(it) != null }

    /**
     * Associates the project with [profileId] (null = follow the default). Returns whether the
     * effective profile changed; if so, stops renewing the previous profile's token from this
     * project (the session itself stays: other projects may share it).
     */
    fun setProfile(profileId: String?): Boolean {
        val before = activeProfile().id
        projectSettings.profileId = profileId
        val changed = activeProfile().id != before
        if (changed) {
            lifecycleManager?.stop()
            lifecycleManager = null
        }
        return changed
    }

    fun isSessionActive(): Boolean = VaultSessionStatus.isActive(activeProfile().id)

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
    fun listMounts(): List<VaultMount> = buildVaultClient(activeProfile()).listMounts()

    /** Direct children of [path] within [mount] ("" = mount root), for the manual picker. */
    fun listSecrets(mount: String, path: String = ""): List<String> =
        buildVaultClient(activeProfile()).listSecrets(mount, path)

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
        val profile = activeProfile()
        val effectiveVersion = version ?: projectSettings.pinnedSecretVersion
        // Profile id first: the same mount/secret on two Vault servers are different secrets.
        val cacheKey = "${profile.id}:${secretPath.mount}.${secretPath.secretName}@${effectiveVersion ?: "latest"}"
        cache.get(cacheKey)?.let { return it }

        val client = buildVaultClient(profile)
        val data = client.readSecret(secretPath.mount, secretPath.secretName, effectiveVersion)
        cache.put(cacheKey, data)
        return data
    }

    /** Lists available versions so the UI can offer a picker. */
    fun fetchVersions(secretPath: SecretPath): VaultSecretMetadata {
        val client = buildVaultClient(activeProfile())
        return client.readMetadata(secretPath.mount, secretPath.secretName)
    }

    /** The user's disabled-keys/edited-values adjustments for [secretPath], empty if none were made. */
    fun overridesFor(secretPath: SecretPath): SecretOverrides = overridesService.get(activeProfile().id, secretPath)

    fun setOverrides(secretPath: SecretPath, overrides: SecretOverrides) {
        overridesService.set(activeProfile().id, secretPath, overrides)
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
        val profile = activeProfile()
        return authenticateAndStore(profile, httpClientFor(profile))
    }

    /** Clears the active profile's session and any secrets cached under it, for an explicit "Logout" action. */
    fun logout() {
        val profileId = activeProfile().id
        lifecycleManager?.stop()
        lifecycleManager = null
        VaultCredentialsStore.clear(profileId)
        VaultSessionStatus.clear(profileId)
        cache.invalidateAll()
        // Edited values are secret material tied to this session; disabled-key exclusions are just
        // names, so they're kept — no reason to make the user redo that on every logout.
        overridesService.discardValues(profileId)
    }

    /**
     * Empties every in-memory store this service owns — the secret cache and every secret's
     * overrides (both disabled keys and edited values) — without touching the session or any
     * `.env` already written to disk. For the explicit "Clear memory" action.
     */
    fun clearMemory() {
        cache.invalidateAll()
        overridesService.clearAll()
    }

    private fun httpClientFor(profile: VaultProfile): HttpClient = HttpClient.newBuilder()
        .sslContext(CustomTlsSocketFactory.buildSslContext(profile.customCaCertPath))
        .build()

    private fun buildVaultClient(profile: VaultProfile): VaultClient {
        val httpClient = httpClientFor(profile)

        val resolvedToken = VaultCredentialsStore.read(profile.id)
            ?: authenticateAndStore(profile, httpClient).clientToken

        return VaultClientImpl(
            vaultUrl = profile.vaultUrl,
            namespace = profile.namespace,
            httpClient = httpClient,
            retryPolicy = RetryPolicy(),
            tokenProvider = { resolvedToken },
        )
    }

    private fun authenticateAndStore(profile: VaultProfile, httpClient: HttpClient): AuthResult {
        val strategy = buildAuthStrategy(profile, httpClient)
        val auth = strategy.authenticate()
        VaultCredentialsStore.store(profile.id, auth.clientToken)
        VaultSessionStatus.update(profile.id, profile.authMethod, auth)
        startLifecycle(profile, strategy, auth)
        return auth
    }

    private fun buildAuthStrategy(profile: VaultProfile, httpClient: HttpClient): VaultAuthStrategy =
        AuthStrategyFactory.create(
            authMethod = profile.authMethod,
            vaultUrl = profile.vaultUrl,
            oidcMountPath = profile.oidcMountPath,
            oidcRole = profile.oidcRole,
            callbackPort = profile.callbackPort,
            callbackPath = profile.callbackPath,
            ldapMountPath = profile.ldapMountPath,
            userpassMountPath = profile.userpassMountPath,
            httpClient = httpClient,
        )

    /**
     * Renews [auth] in the background for as long as [profile] still points at the server that
     * issued it. If the profile is deleted or re-pointed in Settings (which clears its session), the
     * renewal must not resurrect the old server's token under that profile id — so both callbacks
     * check [issuedFor] first, and a stale manager stops itself without touching the store.
     */
    private fun startLifecycle(profile: VaultProfile, strategy: VaultAuthStrategy, auth: AuthResult) {
        val issuedFor = profile.copy()
        val profileId = issuedFor.id
        fun stillSameServer(): Boolean =
            VaultApplicationSettingsService.getInstance().findProfile(profileId)
                ?.let { !it.invalidatesSessionOf(issuedFor) } == true

        lifecycleManager?.stop()
        lateinit var manager: TokenLifecycleManager
        manager = TokenLifecycleManager(
            strategy = strategy,
            onRenewed = {
                if (stillSameServer()) {
                    VaultCredentialsStore.store(profileId, it.clientToken)
                    VaultSessionStatus.update(profileId, strategy.method, it)
                } else {
                    manager.stop()
                }
            },
            onRenewalFailed = {
                if (stillSameServer()) {
                    VaultCredentialsStore.clear(profileId)
                    VaultSessionStatus.clear(profileId)
                }
            },
        )
        lifecycleManager = manager.apply { start(auth) }
    }

    fun disposeProject() {
        lifecycleManager?.stop()
        cache.invalidateAll()
    }
}

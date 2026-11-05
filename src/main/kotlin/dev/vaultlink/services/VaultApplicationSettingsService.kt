package dev.vaultlink.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.XCollection
import dev.vaultlink.core.auth.AuthMethod

enum class EnvApplyStrategy { AUTO, RUN_CONFIG_ONLY, DOTENV_ONLY, BOTH }

class VaultSettingsState {
    /** Every configured Vault connection. Never empty once loaded (see [VaultApplicationSettingsService.normalize]). */
    @XCollection(style = XCollection.Style.v2)
    var profiles: MutableList<VaultProfile> = mutableListOf()

    /** Profile used by projects with no explicit association. */
    var defaultProfileId: String? = null

    // Global (not per-profile) behavior.
    var connectTimeoutMs: Int = 10_000
    var readTimeoutMs: Int = 10_000
    var cacheTtlMinutes: Long = 15
    var envApplyStrategy: EnvApplyStrategy = EnvApplyStrategy.AUTO

    // --- Legacy single-server fields (<= 1.3.0). Only read once, to migrate into a "Default"
    // profile; reset to their defaults afterwards so they stop being written to vaultlink.xml.
    var vaultUrl: String = ""
    var namespace: String? = null
    var authMethod: AuthMethod = AuthMethod.OIDC
    var oidcMountPath: String = "oidc"
    var oidcRole: String = ""
    var ldapMountPath: String = "ldap"
    var userpassMountPath: String = "userpass"
    var callbackPort: Int = 8250
    var callbackPath: String = "/oidc/callback"
    var customCaCertPath: String? = null
}

/** GLOBAL config at the IDE level: a single XML in options/, shared by all open projects. */
@Service(Service.Level.APP)
@State(name = "VaultLinkSettings", storages = [Storage("vaultlink.xml")])
class VaultApplicationSettingsService : PersistentStateComponent<VaultSettingsState> {
    private var myState = VaultSettingsState()

    override fun getState(): VaultSettingsState = myState

    override fun loadState(state: VaultSettingsState) {
        XmlSerializerUtil.copyBean(state, myState)
        normalize()
    }

    override fun noStateLoaded() {
        normalize()
    }

    fun profiles(): List<VaultProfile> {
        normalize()
        return myState.profiles
    }

    fun defaultProfile(): VaultProfile {
        val profiles = profiles()
        return profiles.find { it.id == myState.defaultProfileId } ?: profiles.first()
    }

    /** The profile with [id], or the default one if [id] is null or no longer exists. */
    fun profile(id: String?): VaultProfile = findProfile(id) ?: defaultProfile()

    fun findProfile(id: String?): VaultProfile? = id?.let { wanted -> profiles().find { it.id == wanted } }

    /**
     * Guarantees at least one profile and a valid default. On first load after upgrading from a
     * single-server version, turns the legacy flat fields into a "Default" profile so existing
     * configurations keep working without any user action.
     */
    private fun normalize() {
        val s = myState
        if (s.profiles.isEmpty()) {
            s.profiles.add(
                VaultProfile(
                    name = "Default",
                    vaultUrl = s.vaultUrl,
                    namespace = s.namespace,
                    authMethod = s.authMethod,
                    oidcMountPath = s.oidcMountPath,
                    oidcRole = s.oidcRole,
                    ldapMountPath = s.ldapMountPath,
                    userpassMountPath = s.userpassMountPath,
                    callbackPort = s.callbackPort,
                    callbackPath = s.callbackPath,
                    customCaCertPath = s.customCaCertPath,
                ),
            )
            clearLegacyFields(s)
        }
        if (s.profiles.none { it.id == s.defaultProfileId }) {
            s.defaultProfileId = s.profiles.first().id
        }
    }

    private fun clearLegacyFields(s: VaultSettingsState) {
        val defaults = VaultSettingsState()
        s.vaultUrl = defaults.vaultUrl
        s.namespace = defaults.namespace
        s.authMethod = defaults.authMethod
        s.oidcMountPath = defaults.oidcMountPath
        s.oidcRole = defaults.oidcRole
        s.ldapMountPath = defaults.ldapMountPath
        s.userpassMountPath = defaults.userpassMountPath
        s.callbackPort = defaults.callbackPort
        s.callbackPath = defaults.callbackPath
        s.customCaCertPath = defaults.customCaCertPath
    }

    companion object {
        fun getInstance(): VaultApplicationSettingsService =
            ApplicationManager.getApplication().getService(VaultApplicationSettingsService::class.java)
    }
}

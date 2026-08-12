package dev.vaultlink.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import dev.vaultlink.core.auth.AuthMethod

enum class EnvApplyStrategy { AUTO, RUN_CONFIG_ONLY, DOTENV_ONLY }

class VaultSettingsState {
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
    var connectTimeoutMs: Int = 10_000
    var readTimeoutMs: Int = 10_000
    var cacheTtlMinutes: Long = 15
    var envApplyStrategy: EnvApplyStrategy = EnvApplyStrategy.AUTO
}

/** GLOBAL config at the IDE level: a single XML in options/, shared by all open projects. */
@Service(Service.Level.APP)
@State(name = "VaultLinkSettings", storages = [Storage("vaultlink.xml")])
class VaultApplicationSettingsService : PersistentStateComponent<VaultSettingsState> {
    private var myState = VaultSettingsState()

    override fun getState(): VaultSettingsState = myState

    override fun loadState(state: VaultSettingsState) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    companion object {
        fun getInstance(): VaultApplicationSettingsService =
            ApplicationManager.getApplication().getService(VaultApplicationSettingsService::class.java)
    }
}

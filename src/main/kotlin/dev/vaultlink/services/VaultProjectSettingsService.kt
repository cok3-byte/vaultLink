package dev.vaultlink.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

enum class SecretResolutionMode { AUTO, MANUAL }

class VaultProjectSettingsState {
    var pinnedSecretVersion: Int? = null // null = always "latest"
    var resolutionMode: SecretResolutionMode = SecretResolutionMode.AUTO
    var manualMount: String? = null // only used when resolutionMode == MANUAL
    var manualSecretName: String? = null // only used when resolutionMode == MANUAL
    var envApplyStrategyOverride: EnvApplyStrategy? = null
    var targetRunConfigurationName: String? = null // null = apply to every compatible Run Configuration
}

/** Project-specific overrides; XML in .idea/ with roamingType DISABLED, added to .gitignore when created. */
@Service(Service.Level.PROJECT)
@State(
    name = "VaultLinkProjectSettings",
    storages = [Storage("vaultlink.xml", roamingType = RoamingType.DISABLED)],
)
class VaultProjectSettingsService : PersistentStateComponent<VaultProjectSettingsState> {
    private var myState = VaultProjectSettingsState()

    override fun getState(): VaultProjectSettingsState = myState

    override fun loadState(state: VaultProjectSettingsState) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    companion object {
        fun getInstance(project: Project): VaultProjectSettingsService =
            project.getService(VaultProjectSettingsService::class.java)
    }
}

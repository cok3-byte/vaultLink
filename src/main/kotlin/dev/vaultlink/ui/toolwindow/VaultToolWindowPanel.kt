package dev.vaultlink.ui.toolwindow

import com.intellij.execution.CommonJavaRunConfigurationParameters
import com.intellij.execution.RunManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import dev.vaultlink.core.vault.model.SecretPath
import dev.vaultlink.core.vault.model.VaultSecretData
import dev.vaultlink.core.vault.model.VaultSecretMetadata
import dev.vaultlink.services.EnvApplyStrategy
import dev.vaultlink.services.SecretApplicationCoordinator
import dev.vaultlink.services.SecretResolutionMode
import dev.vaultlink.services.VaultApplicationSettingsService
import dev.vaultlink.services.VaultProjectService
import dev.vaultlink.services.VaultProjectSettingsService
import dev.vaultlink.ui.common.LoginNotifier
import dev.vaultlink.ui.common.SecretVariablesPanel
import dev.vaultlink.ui.common.VaultSessionStatusPanel
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/** null [configName] represents "apply to every compatible Run Configuration". */
private data class RunConfigEntry(val displayName: String, val configName: String?) {
    override fun toString(): String = displayName
}

/**
 * Shows the currently resolved mount/secret (AUTO: parsed from the project name; MANUAL: picked
 * via [MountSecretPickerDialog]), the current session, and lets you trigger the fetch.
 */
class VaultToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = project.getService(VaultProjectService::class.java)
    private val projectSettings get() = VaultProjectSettingsService.getInstance(project).state

    private val statusPanel = VaultSessionStatusPanel()
    private val variablesPanel = SecretVariablesPanel()
    private var versionLabel: JLabel? = null
    private var sourceLabel: JLabel? = null
    private var fetchButton: JButton? = null
    private var chooseVersionButton: JButton? = null
    private lateinit var browseRow: Row

    init {
        val runConfigEntries = listOf(RunConfigEntry("All Run Configurations", null)) +
            RunManager.getInstance(project).allSettings
                .filter { it.configuration is CommonJavaRunConfigurationParameters }
                .map { RunConfigEntry("${it.type.displayName} → ${it.name}", it.name) }
        val showRunConfigTarget =
            VaultApplicationSettingsService.getInstance().state.envApplyStrategy != EnvApplyStrategy.DOTENV_ONLY

        lateinit var runConfigGroupRow: Row

        val content = panel {
            row {
                cell(statusPanel)
            }
            separator()

            group("Secret Source") {
                row("Mode:") {
                    comboBox(SecretResolutionMode.entries).bindItem(
                        { projectSettings.resolutionMode },
                        { mode ->
                            service.setResolutionMode(mode ?: SecretResolutionMode.AUTO)
                            refreshSecretSource()
                        },
                    )
                }.rowComment("AUTO parses <mount>.<secret> from the project name. MANUAL uses the mount/secret picked below.")
                row {
                    sourceLabel = label(sourceText()).component
                }
                browseRow = row {
                    button("Browse...") {
                        val picked = MountSecretPickerDialog.pick(project, service) ?: return@button
                        service.setManualSelection(picked.mount, picked.secretName)
                        refreshSecretSource()
                    }
                }.rowComment("Navigates the Vault API: pick a KV v2 mount, then drill into its secrets.")
                row {
                    versionLabel = label(versionText(service)).component
                }
            }

            group("Action") {
                row {
                    fetchButton = button("Fetch secret") { fetchSecret(service, service.resolveSecretPath()) }
                        .enabled(service.resolveSecretPath() != null).component
                    chooseVersionButton = button("Choose version...") { chooseVersion(service, service.resolveSecretPath()) }
                        .enabled(service.resolveSecretPath() != null).component
                }.rowComment("Authenticates (if needed) and applies the secret according to the strategy configured in Settings.")
            }

            runConfigGroupRow = group("Run Config Target") {
                row("Applies to:") {
                    comboBox(runConfigEntries).bindItem(
                        { runConfigEntries.find { it.configName == projectSettings.targetRunConfigurationName } ?: runConfigEntries.first() },
                        { selected -> projectSettings.targetRunConfigurationName = selected?.configName },
                    )
                }.rowComment("Only relevant for AUTO/RUN_CONFIG_ONLY — filters which Run Configuration receives the live env var injection.")
            }

            group("Variables") {
                row {
                    cell(variablesPanel)
                }.rowComment("Values are masked by default — click the eye button to reveal/hide them.")
            }
        }
        runConfigGroupRow.visible(showRunConfigTarget)
        browseRow.visible(projectSettings.resolutionMode == SecretResolutionMode.MANUAL)
        content.border = JBUI.Borders.empty(12)
        add(content, BorderLayout.CENTER)
    }

    private fun sourceText(): String {
        val secretPath = service.resolveSecretPath()
        return when {
            secretPath != null -> "mount=${secretPath.mount}, secret=${secretPath.secretName}"
            projectSettings.resolutionMode == SecretResolutionMode.MANUAL -> "No secret selected — click Browse to pick one"
            else -> "The project name does not follow the <mount>.<secret> pattern"
        }
    }

    /** Recomputes the resolved secret path (mode may have changed, or a manual pick was made) and updates the UI. */
    private fun refreshSecretSource() {
        sourceLabel?.text = sourceText()
        browseRow.visible(projectSettings.resolutionMode == SecretResolutionMode.MANUAL)
        val hasSecretPath = service.resolveSecretPath() != null
        fetchButton?.isEnabled = hasSecretPath
        chooseVersionButton?.isEnabled = hasSecretPath
    }

    private fun versionText(service: VaultProjectService): String =
        service.pinnedVersion()?.let { "Version: pinned to v$it" } ?: "Version: latest"

    private fun fetchSecret(service: VaultProjectService, secretPath: SecretPath?) {
        if (secretPath == null) return
        object : Task.Backgroundable(project, "VaultLink: fetching secret...", true) {
            private var result: Result<VaultSecretData>? = null

            override fun run(indicator: ProgressIndicator) {
                result = runCatching {
                    val secret = service.fetchSecret(secretPath)
                    val strategy = VaultApplicationSettingsService.getInstance().state.envApplyStrategy
                    SecretApplicationCoordinator(project).apply(secret, strategy, hasJvmRunConfig = false)
                    secret
                }
            }

            // IntelliJ calls onSuccess() on the EDT after run() — this is where it's safe to touch Swing.
            override fun onSuccess() {
                val secret = result?.getOrNull()
                if (secret != null) {
                    statusPanel.refresh()
                    variablesPanel.setSecret(secret)
                    LoginNotifier.notifySuccess(
                        project,
                        "Vault: secret applied",
                        "${secretPath.mount}.${secretPath.secretName}",
                    )
                } else {
                    LoginNotifier.notifyError(project, result?.exceptionOrNull() ?: IllegalStateException("Unknown error"))
                }
            }
        }.queue()
    }

    private fun chooseVersion(service: VaultProjectService, secretPath: SecretPath?) {
        if (secretPath == null) return
        object : Task.Backgroundable(project, "VaultLink: listing secret versions...", true) {
            private var result: Result<VaultSecretMetadata>? = null

            override fun run(indicator: ProgressIndicator) {
                result = runCatching { service.fetchVersions(secretPath) }
            }

            override fun onSuccess() {
                val metadata = result?.getOrNull()
                if (metadata == null) {
                    LoginNotifier.notifyError(project, result?.exceptionOrNull() ?: IllegalStateException("Unknown error"))
                    return
                }
                val choice = SecretVersionPickerDialog.pick(metadata.currentVersion, metadata.versions) ?: return
                when (choice) {
                    is VersionChoice.Latest -> service.setPinnedVersion(null)
                    is VersionChoice.Specific -> service.setPinnedVersion(choice.version)
                }
                versionLabel?.text = versionText(service)
                variablesPanel.setSecret(null)
                LoginNotifier.notifySuccess(project, "Vault: version updated", versionText(service))
            }
        }.queue()
    }
}

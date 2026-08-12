package dev.vaultlink.ui.toolwindow

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import dev.vaultlink.core.vault.model.SecretPath
import dev.vaultlink.core.vault.model.VaultSecretData
import dev.vaultlink.core.vault.model.VaultSecretMetadata
import dev.vaultlink.services.SecretApplicationCoordinator
import dev.vaultlink.services.VaultApplicationSettingsService
import dev.vaultlink.services.VaultProjectService
import dev.vaultlink.ui.common.LoginNotifier
import dev.vaultlink.ui.common.SecretVariablesPanel
import dev.vaultlink.ui.common.VaultSessionStatusPanel
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

/** Shows the mount/secret detected from the project name, the current session, and lets you trigger the fetch. */
class VaultToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val statusPanel = VaultSessionStatusPanel()
    private val variablesPanel = SecretVariablesPanel()
    private var versionLabel: JLabel? = null

    init {
        val service = project.getService(VaultProjectService::class.java)
        val secretPath = service.resolveSecretPath()

        val content = panel {
            row {
                cell(statusPanel)
            }
            separator()

            group("Detected project") {
                row {
                    label(
                        if (secretPath != null) {
                            "mount=${secretPath.mount}, secret=${secretPath.secretName}"
                        } else {
                            "The project name does not follow the <mount>.<secret> pattern"
                        },
                    )
                }
                row {
                    versionLabel = label(versionText(service)).component
                }
            }

            group("Action") {
                row {
                    button("Fetch secret") { fetchSecret(service, secretPath) }.enabled(secretPath != null)
                    button("Choose version...") { chooseVersion(service, secretPath) }.enabled(secretPath != null)
                }.rowComment("Authenticates (if needed) and applies the secret according to the strategy configured in Settings.")
            }

            group("Variables") {
                row {
                    cell(variablesPanel)
                }.rowComment("Values are masked by default — click the eye button to reveal/hide them.")
            }
        }
        content.border = JBUI.Borders.empty(12)
        add(content, BorderLayout.CENTER)
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

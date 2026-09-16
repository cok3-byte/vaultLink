package dev.vaultlink.ui.toolwindow

import com.intellij.execution.CommonJavaRunConfigurationParameters
import com.intellij.execution.RunManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import dev.vaultlink.core.auth.AuthResult
import dev.vaultlink.core.credentials.VaultSessionStatus
import dev.vaultlink.core.vault.model.SecretPath
import dev.vaultlink.core.vault.model.VaultSecretData
import dev.vaultlink.core.vault.model.VaultSecretMetadata
import dev.vaultlink.services.EnvApplyStrategy
import dev.vaultlink.services.SecretApplicationCoordinator
import dev.vaultlink.services.SecretResolutionMode
import dev.vaultlink.services.VaultApplicationSettingsService
import dev.vaultlink.services.VaultProjectService
import dev.vaultlink.services.VaultProjectSettingsService
import dev.vaultlink.ui.common.ChipVariant
import dev.vaultlink.ui.common.LoginNotifier
import dev.vaultlink.ui.common.SecretVariablesPanel
import dev.vaultlink.ui.common.VaultSessionStatusPanel
import dev.vaultlink.ui.common.chip
import dev.vaultlink.ui.settings.VaultSettingsConfigurable
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/** null [configName] represents "apply to every compatible Run Configuration". */
private data class RunConfigEntry(val displayName: String, val configName: String?) {
    override fun toString(): String = displayName
}

private fun SecretPath.display(): String = "$mount.$secretName"

/**
 * Shows the currently resolved mount/secret (AUTO: parsed from the project name; MANUAL: picked
 * via [MountSecretPickerDialog]), the current session, and lets you trigger the fetch.
 *
 * Layout follows the redesign spec: read-only facts (session, secret, version) lead in a header
 * card, the primary action (Fetch secret) sits right below it, the fetched Variables are the next
 * thing you see, and configuration (mode, browse, run-config target) is demoted into a collapsed
 * "Advanced" section at the bottom. The same triggers are also exposed as title-bar quick actions
 * (see [createTitleActions]) so they stay reachable while the panel is scrolled.
 */
class VaultToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = project.getService(VaultProjectService::class.java)
    private val projectSettings get() = VaultProjectSettingsService.getInstance(project).state

    private val headerContainer = JPanel(BorderLayout())
    private val variablesPanel = SecretVariablesPanel()
    private var fetchButton: JButton? = null
    private var versionButton: JButton? = null
    private lateinit var browseRow: Row

    init {
        val runConfigEntries = listOf(RunConfigEntry("All Run Configurations", null)) +
            RunManager.getInstance(project).allSettings
                .filter { it.configuration is CommonJavaRunConfigurationParameters }
                .map { RunConfigEntry("${it.type.displayName} → ${it.name}", it.name) }
        val showRunConfigTarget =
            VaultApplicationSettingsService.getInstance().state.envApplyStrategy != EnvApplyStrategy.DOTENV_ONLY

        val content = panel {
            row {
                cell(headerContainer)
            }

            row {
                fetchButton = button("Fetch secret") { fetchSecret(service.resolveSecretPath()) }
                    .component.apply { icon = AllIcons.Actions.Download }
                versionButton = button("Version") { chooseVersion(service.resolveSecretPath()) }
                    .component.apply { icon = AllIcons.Vcs.History }
            }

            row {
                cell(variablesPanel)
            }

            collapsibleGroup("Advanced") {
                row("Mode:") {
                    comboBox(SecretResolutionMode.entries).bindItem(
                        { projectSettings.resolutionMode },
                        { mode ->
                            service.setResolutionMode(mode ?: SecretResolutionMode.AUTO)
                            refreshAll()
                        },
                    )
                }.rowComment("AUTO parses <mount>.<secret> from the project name. MANUAL uses the mount/secret picked below.")
                browseRow = row {
                    button("Browse…") {
                        val picked = MountSecretPickerDialog.pick(project, service) ?: return@button
                        service.setManualSelection(picked.mount, picked.secretName)
                        refreshAll()
                    }
                }.rowComment("Navigates the Vault API: pick a KV v2 mount, then drill into its secrets.")
                if (showRunConfigTarget) {
                    row("Applies to:") {
                        comboBox(runConfigEntries).bindItem(
                            { runConfigEntries.find { it.configName == projectSettings.targetRunConfigurationName } ?: runConfigEntries.first() },
                            { selected -> projectSettings.targetRunConfigurationName = selected?.configName },
                        )
                    }.rowComment("Only relevant for AUTO/RUN_CONFIG_ONLY — filters which Run Configuration receives the live env var injection.")
                }
            }
        }
        browseRow.visible(projectSettings.resolutionMode == SecretResolutionMode.MANUAL)
        content.border = JBUI.Borders.empty(12)

        val scrollPane = JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
        add(scrollPane, BorderLayout.CENTER)
        refreshAll()
    }

    /** Title-bar quick actions — Fetch secret, Choose version, Login/Logout, Settings — mirroring the in-body triggers. */
    fun createTitleActions(): List<AnAction> = listOf(
        FetchSecretTitleAction({ service.resolveSecretPath() != null }) { fetchSecret(service.resolveSecretPath()) },
        ChooseVersionTitleAction({ service.resolveSecretPath() != null }) { chooseVersion(service.resolveSecretPath()) },
        ToggleSessionTitleAction(
            { VaultSessionStatus.isActive() },
            { login() },
            { logout() },
        ),
        OpenVaultSettingsTitleAction {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, VaultSettingsConfigurable::class.java)
        },
    )

    private fun login() {
        object : Task.Backgroundable(project, "VaultLink: logging in...", true) {
            private var result: Result<AuthResult>? = null

            override fun run(indicator: ProgressIndicator) {
                result = runCatching { service.login() }
            }

            override fun onSuccess() {
                if (result?.isSuccess == true) refreshAll()
                val method = VaultApplicationSettingsService.getInstance().state.authMethod
                LoginNotifier.notifyResult(project, method, result ?: Result.failure(IllegalStateException("Unknown error")))
            }
        }.queue()
    }

    private fun logout() {
        service.logout()
        variablesPanel.setSecret(null)
        refreshAll()
        LoginNotifier.notifySuccess(project, "Vault: logged out", "Session cleared")
    }

    /** Rebuilds the header card and refreshes every control that depends on session/secret/mode state. */
    private fun refreshAll() {
        headerContainer.removeAll()
        headerContainer.add(buildHeaderCard(), BorderLayout.CENTER)
        headerContainer.revalidate()
        headerContainer.repaint()

        val hasSecretPath = service.resolveSecretPath() != null
        fetchButton?.isEnabled = hasSecretPath
        versionButton?.isEnabled = hasSecretPath
        if (::browseRow.isInitialized) {
            browseRow.visible(projectSettings.resolutionMode == SecretResolutionMode.MANUAL)
        }
        variablesPanel.setEmptyText(emptyVariablesText())
    }

    private fun buildHeaderCard(): JPanel {
        val statusPanel = VaultSessionStatusPanel()
        val secretLine = buildSecretLine()
        val chipsRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false
            add(chip(projectSettings.resolutionMode.name))
            val secretPath = service.resolveSecretPath()
            when {
                secretPath != null -> add(versionChip())
                projectSettings.resolutionMode == SecretResolutionMode.AUTO -> add(chip("no match", ChipVariant.WARNING))
            }
        }
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(2, 0, 8, 0)
            add(statusPanel)
            add(Box.createVerticalStrut(6))
            add(secretLine)
            add(Box.createVerticalStrut(6))
            add(chipsRow)
        }
    }

    private fun versionChip() =
        service.pinnedVersion()?.let { chip("v$it pinned", ChipVariant.ACCENT) } ?: chip("latest")

    private fun buildSecretLine(): JPanel {
        val secretPath = service.resolveSecretPath()
        val panel = JPanel(BorderLayout(8, 0)).apply { isOpaque = false }
        when {
            secretPath != null -> {
                val label = JBLabel(secretPath.display()).apply { font = Font(Font.MONOSPACED, Font.BOLD, font.size) }
                panel.add(label, BorderLayout.WEST)
            }
            projectSettings.resolutionMode == SecretResolutionMode.MANUAL -> {
                val label = JBLabel("No secret selected yet — Browse the Vault mounts.").apply { foreground = JBColor.GRAY }
                val browse = JButton("Browse…", AllIcons.Actions.Find).apply {
                    addActionListener {
                        val picked = MountSecretPickerDialog.pick(project, service) ?: return@addActionListener
                        service.setManualSelection(picked.mount, picked.secretName)
                        refreshAll()
                    }
                }
                panel.add(label, BorderLayout.CENTER)
                panel.add(browse, BorderLayout.EAST)
            }
            else -> {
                val label = JBLabel("This project name is not <mount>.<secret> — switch to Manual and browse.").apply {
                    foreground = JBColor.GRAY
                    icon = AllIcons.General.Warning
                }
                panel.add(label, BorderLayout.CENTER)
            }
        }
        return panel
    }

    private fun emptyVariablesText(): String {
        val secretPath = service.resolveSecretPath()
        return when {
            secretPath != null -> "No variables yet — Fetch secret to load them."
            projectSettings.resolutionMode == SecretResolutionMode.MANUAL -> "No variables yet — pick a secret to load them."
            else -> "No variables yet — resolve a secret first."
        }
    }

    private fun fetchSecret(secretPath: SecretPath?) {
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
                    refreshAll()
                    variablesPanel.setSecret(secret)
                    LoginNotifier.notifySuccess(project, "Vault: secret applied", secretPath.display())
                } else {
                    LoginNotifier.notifyError(project, result?.exceptionOrNull() ?: IllegalStateException("Unknown error"))
                }
            }
        }.queue()
    }

    private fun chooseVersion(secretPath: SecretPath?) {
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
                variablesPanel.setSecret(null)
                refreshAll()
                LoginNotifier.notifySuccess(
                    project,
                    "Vault: version updated",
                    service.pinnedVersion()?.let { "Version: pinned to v$it" } ?: "Version: latest",
                )
            }
        }.queue()
    }
}

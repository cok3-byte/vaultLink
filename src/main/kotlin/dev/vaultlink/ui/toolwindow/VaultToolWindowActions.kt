package dev.vaultlink.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Title-bar quick actions for the VaultLink tool window — kept reachable even when the panel is
 * scrolled. Each one is a thin wrapper around the same trigger [VaultToolWindowPanel] uses for its
 * in-body buttons, so both paths refresh identically. Registered via [com.intellij.openapi.wm.ToolWindow.setTitleActions]
 * from [VaultToolWindowFactory] — no plugin.xml action id is needed for that API.
 */
class FetchSecretTitleAction(
    private val isEnabled: () -> Boolean,
    private val perform: () -> Unit,
) : AnAction("Fetch Secret", "Login (if needed) and apply the resolved secret", AllIcons.Actions.Download) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = isEnabled()
    }
    override fun actionPerformed(e: AnActionEvent) = perform()
}

class ChooseVersionTitleAction(
    private val isEnabled: () -> Boolean,
    private val perform: () -> Unit,
) : AnAction("Choose Version…", "Pin a specific KV v2 version, or go back to latest", AllIcons.Vcs.History) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = isEnabled()
    }
    override fun actionPerformed(e: AnActionEvent) = perform()
}

/** Single toolbar slot that swaps between Login and Logout depending on the current session state. */
class ToggleSessionTitleAction(
    private val isActive: () -> Boolean,
    private val login: () -> Unit,
    private val logout: () -> Unit,
) : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val active = isActive()
        e.presentation.text = if (active) "Logout" else "Login"
        e.presentation.description =
            if (active) "Clear the current Vault session" else "Authenticate with Vault ahead of time"
        e.presentation.icon = if (active) AllIcons.Actions.Exit else AllIcons.Actions.Execute
    }
    override fun actionPerformed(e: AnActionEvent) {
        if (isActive()) logout() else login()
    }
}

class OpenVaultSettingsTitleAction(
    private val perform: () -> Unit,
) : AnAction("VaultLink Settings", "Open Settings ▸ Tools ▸ VaultLink", AllIcons.General.Settings) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) = perform()
}

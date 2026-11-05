package dev.vaultlink.ui.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import dev.vaultlink.core.auth.AuthMethod
import dev.vaultlink.core.auth.AuthResult
import dev.vaultlink.core.auth.AuthStrategyFactory
import dev.vaultlink.core.credentials.VaultCredentialsStore
import dev.vaultlink.core.credentials.VaultSessionStatus
import dev.vaultlink.core.net.CustomTlsSocketFactory
import dev.vaultlink.services.EnvApplyStrategy
import dev.vaultlink.services.VaultApplicationSettingsService
import dev.vaultlink.services.VaultProfile
import dev.vaultlink.services.newProfileId
import dev.vaultlink.ui.common.LoginNotifier
import dev.vaultlink.ui.common.VaultSessionStatusPanel
import dev.vaultlink.ui.design.VaultSpacing
import java.awt.event.ItemEvent
import java.net.http.HttpClient
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Settings > Tools > VaultLink — parentId="tools", global config shared by every project.
 *
 * Manages a list of Vault **profiles** (one per server) plus the global behavior (variable
 * strategy, timeouts, cache). The Connection/Authentication groups edit the profile selected in
 * the Profile combo; every edit happens on working copies and only reaches the persisted state in
 * [apply], so Cancel discards everything — including added/deleted profiles.
 */
class VaultSettingsConfigurable : Configurable {

    private val appSettings get() = VaultApplicationSettingsService.getInstance()
    private val settings get() = appSettings.state

    // Working copies — what the form edits until apply().
    private var profiles: MutableList<VaultProfile> = mutableListOf()
    private var defaultProfileId: String? = null
    private var current: VaultProfile = VaultProfile()

    // Global (not per-profile) values, bound via Kotlin UI DSL v2.
    private var envApplyStrategy = EnvApplyStrategy.AUTO
    private var connectTimeoutMs = 10_000
    private var readTimeoutMs = 10_000
    private var cacheTtlMinutes = 15

    private var panel: DialogPanel? = null
    private var statusPanel: VaultSessionStatusPanel? = null
    private val profileModel = DefaultComboBoxModel<VaultProfile>()
    private var profileCombo: ComboBox<VaultProfile>? = null
    private var deleteButton: JButton? = null
    private var setDefaultButton: JButton? = null
    private var updateFieldVisibility: (AuthMethod) -> Unit = {}

    /** True while the combo's model/selection is changed programmatically, so its listener ignores it. */
    private var syncingCombo = false

    override fun getDisplayName(): String = "VaultLink"

    override fun createComponent(): JComponent {
        loadFromSettings()

        lateinit var oidcMountRow: Row
        lateinit var oidcRoleRow: Row
        lateinit var callbackPortRow: Row
        lateinit var oidcNoteRow: Row
        lateinit var ldapMountRow: Row
        lateinit var userpassMountRow: Row
        lateinit var userpassNoteRow: Row
        lateinit var tokenNoteRow: Row
        lateinit var callbackPathRow: Row

        updateFieldVisibility = { method ->
            oidcMountRow.visible(method == AuthMethod.OIDC)
            oidcRoleRow.visible(method == AuthMethod.OIDC)
            callbackPortRow.visible(method == AuthMethod.OIDC)
            callbackPathRow.visible(method == AuthMethod.OIDC)
            oidcNoteRow.visible(method == AuthMethod.OIDC)
            ldapMountRow.visible(method == AuthMethod.LDAP)
            userpassMountRow.visible(method == AuthMethod.USERPASS)
            userpassNoteRow.visible(method == AuthMethod.USERPASS)
            tokenNoteRow.visible(method == AuthMethod.TOKEN)
        }

        val statusComponent = VaultSessionStatusPanel { current.id }
        statusPanel = statusComponent

        val dialogPanel = panel {
            group("Profile") {
                row("Profile:") {
                    profileCombo = comboBox(profileModel, ProfileRenderer()).component.apply {
                        addItemListener { event ->
                            if (!syncingCombo && event.stateChange == ItemEvent.SELECTED) {
                                switchTo(event.item as VaultProfile)
                            }
                        }
                    }
                    button("Add") { addProfile(VaultProfile(name = uniqueName("New profile"))) }
                        .component.icon = AllIcons.General.Add
                    button("Duplicate") {
                        panel?.apply()
                        addProfile(current.copy(id = newProfileId(), name = uniqueName("${current.name} copy")))
                    }.component.icon = AllIcons.Actions.Copy
                    deleteButton = button("Delete") { deleteCurrent() }
                        .component.apply { icon = AllIcons.General.Remove }
                    setDefaultButton = button("Set as default") {
                        defaultProfileId = current.id
                        refreshProfileControls()
                    }.component
                }.rowComment(
                    "One profile per Vault server. Each project uses the default profile unless another one " +
                        "is picked in the VaultLink tool window.",
                )
                row("Name:") {
                    textField()
                        .bindText({ current.name }, { current.name = it })
                        .validationOnInput { if (it.text.isBlank()) error("Profile name is required.") else null }
                        .component.also(::liveRenameCombo)
                }
                row {
                    cell(statusComponent)
                }
            }

            group("Connection") {
                row("Vault URL:") {
                    textField().bindText({ current.vaultUrl }, { current.vaultUrl = it }).validationOnInput {
                        when {
                            it.text.isBlank() -> error("Vault URL is required.")
                            !it.text.startsWith("http://") && !it.text.startsWith("https://") ->
                                error("Vault URL must start with http:// or https://.")
                            else -> null
                        }
                    }
                }
                row("Namespace:") {
                    textField()
                        .bindText({ current.namespace.orEmpty() }, { current.namespace = it.ifBlank { null } })
                        .validationOnInput {
                            if (it.text.any { c -> c.isWhitespace() }) error("Namespace cannot contain spaces.") else null
                        }
                }
                row("Custom CA (PEM/JKS):") {
                    textFieldWithBrowseButton()
                        .bindText({ current.customCaCertPath.orEmpty() }, { current.customCaCertPath = it.ifBlank { null } })
                }.rowComment("Optional — only if Vault uses an internal CA not recognized by the system.")
            }

            group("Authentication") {
                row("Auth method:") {
                    val combo = comboBox(AuthMethod.entries)
                        .bindItem({ current.authMethod }, { current.authMethod = it ?: AuthMethod.OIDC })
                        .component
                    combo.addItemListener { event ->
                        if (event.stateChange == ItemEvent.SELECTED) {
                            updateFieldVisibility(event.item as AuthMethod)
                        }
                    }
                }
                oidcMountRow = row("OIDC mount:") {
                    textField().bindText({ current.oidcMountPath }, { current.oidcMountPath = it })
                }
                oidcRoleRow = row("OIDC role:") {
                    textField().bindText({ current.oidcRole }, { current.oidcRole = it })
                }
                callbackPortRow = row("Callback port (OIDC):") {
                    intTextField(1..65535).bindIntText({ current.callbackPort }, { current.callbackPort = it })
                }
                callbackPathRow = row("Callback path (OIDC):") {
                    textField().bindText({ current.callbackPath }, { current.callbackPath = it })
                }.rowComment("Auto-derived default — rarely needs changing.")
                oidcNoteRow = row {
                    comment("Not implemented yet — OIDC login is planned but the plugin currently authenticates via LDAP or Token.")
                }
                ldapMountRow = row("LDAP mount:") {
                    textField().bindText({ current.ldapMountPath }, { current.ldapMountPath = it })
                }
                userpassMountRow = row("Userpass mount:") {
                    textField().bindText({ current.userpassMountPath }, { current.userpassMountPath = it })
                }
                userpassNoteRow = row {
                    comment("Not implemented yet — Userpass login is planned but the plugin currently authenticates via LDAP or Token.")
                }
                tokenNoteRow = row {
                    comment("Token authentication asks for the token at login time — there is nothing to configure here.")
                }
                row {
                    button("Test login") { testLogin() }
                }.rowComment("Emulates login against the selected profile with the values from this form (no need to Apply first).")
            }

            group("Environment Variables") {
                row("Apply variables:") {
                    comboBox(EnvApplyStrategy.entries)
                        .bindItem({ envApplyStrategy }, { envApplyStrategy = it ?: EnvApplyStrategy.AUTO })
                }.rowComment(
                    "AUTO: injects into the Run Config if it's JVM, otherwise generates .env. " +
                        "RUN_CONFIG_ONLY: only JVM Run/Debug (Node/Python/Docker get nothing). " +
                        "DOTENV_ONLY: always .env.",
                )
            }

            collapsibleGroup("Advanced") {
                row("Connect timeout (ms):") { intTextField(1000..120_000).bindIntText(::connectTimeoutMs) }
                row("Read timeout (ms):") { intTextField(1000..120_000).bindIntText(::readTimeoutMs) }
                row("Cache TTL (minutes):") { intTextField(0..1440).bindIntText(::cacheTtlMinutes) }
            }
        }
        dialogPanel.border = JBUI.Borders.empty(VaultSpacing.LG)

        panel = dialogPanel
        rebuildProfileModel()
        updateFieldVisibility(current.authMethod)
        refreshProfileControls()
        return dialogPanel
    }

    override fun isModified(): Boolean {
        if (panel?.isModified() == true) return true
        return profiles != settings.profiles ||
            defaultProfileId != appSettings.defaultProfile().id
    }

    override fun apply() {
        panel?.apply()
        validateProfiles()

        val persisted = settings.profiles.associateBy { it.id }
        val edited = profiles.associateBy { it.id }
        // A session belongs to a server: drop it when its profile is deleted or now points elsewhere,
        // so the old token is never sent to the new URL/namespace/auth backend.
        persisted.values
            .filter { old -> edited[old.id]?.invalidatesSessionOf(old) ?: true }
            .forEach { clearSession(it.id) }

        settings.profiles = profiles.map { it.copy() }.toMutableList()
        settings.defaultProfileId = defaultProfileId
        settings.envApplyStrategy = envApplyStrategy
        settings.connectTimeoutMs = connectTimeoutMs
        settings.readTimeoutMs = readTimeoutMs
        settings.cacheTtlMinutes = cacheTtlMinutes.toLong()
        statusPanel?.refresh()
    }

    override fun reset() {
        val selectedId = current.id
        loadFromSettings()
        current = profiles.find { it.id == selectedId } ?: current
        rebuildProfileModel()
        panel?.reset()
        updateFieldVisibility(current.authMethod)
        refreshProfileControls()
    }

    private fun loadFromSettings() {
        profiles = appSettings.profiles().map { it.copy() }.toMutableList()
        defaultProfileId = appSettings.defaultProfile().id
        current = profiles.find { it.id == defaultProfileId } ?: profiles.first()
        envApplyStrategy = settings.envApplyStrategy
        connectTimeoutMs = settings.connectTimeoutMs
        readTimeoutMs = settings.readTimeoutMs
        cacheTtlMinutes = settings.cacheTtlMinutes.toInt()
    }

    // --- Profile management --------------------------------------------------------------------

    /** Saves the form into the profile being left, then loads [target] into the form. */
    private fun switchTo(target: VaultProfile) {
        if (target === current) return
        panel?.apply()
        current = target
        setComboSelection(target)
        panel?.reset()
        updateFieldVisibility(current.authMethod)
        refreshProfileControls()
    }

    private fun addProfile(profile: VaultProfile) {
        panel?.apply()
        profiles.add(profile)
        rebuildProfileModel()
        switchTo(profile)
    }

    private fun deleteCurrent() {
        if (profiles.size <= 1) return
        val answer = Messages.showYesNoDialog(
            panel!!,
            "Delete the profile \"${current.name}\"? Projects associated with it will fall back to the " +
                "default profile. Nothing changes until you click Apply/OK.",
            "Delete Vault Profile",
            AllIcons.General.QuestionDialog,
        )
        if (answer != Messages.YES) return

        panel?.apply() // keep unsaved edits in the global fields
        val removed = current
        profiles.remove(removed)
        if (defaultProfileId == removed.id) defaultProfileId = profiles.first().id
        current = profiles.find { it.id == defaultProfileId } ?: profiles.first()
        rebuildProfileModel()
        panel?.reset()
        updateFieldVisibility(current.authMethod)
        refreshProfileControls()
    }

    private fun validateProfiles() {
        profiles.forEach { p ->
            if (p.name.isBlank()) throw ConfigurationException("Every Vault profile needs a name.")
        }
        val duplicated = profiles.groupBy { it.name.trim().lowercase() }.filterValues { it.size > 1 }.keys
        if (duplicated.isNotEmpty()) {
            throw ConfigurationException("Profile names must be unique (\"${duplicated.first()}\" is repeated).")
        }
    }

    private fun uniqueName(base: String): String {
        val taken = profiles.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base $it" }.first { it.lowercase() !in taken }
    }

    private fun rebuildProfileModel() {
        syncingCombo = true
        try {
            profileModel.removeAllElements()
            profiles.forEach(profileModel::addElement)
            profileModel.selectedItem = current
        } finally {
            syncingCombo = false
        }
    }

    private fun setComboSelection(profile: VaultProfile) {
        syncingCombo = true
        try {
            profileModel.selectedItem = profile
        } finally {
            syncingCombo = false
        }
    }

    private fun refreshProfileControls() {
        deleteButton?.isEnabled = profiles.size > 1
        setDefaultButton?.isEnabled = current.id != defaultProfileId
        profileCombo?.repaint()
        statusPanel?.refresh()
    }

    /** Typing in the Name field updates the combo label right away (the bound value is saved on switch/apply too). */
    private fun liveRenameCombo(field: JBTextField) {
        field.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = rename()
            override fun removeUpdate(e: DocumentEvent) = rename()
            override fun changedUpdate(e: DocumentEvent) = rename()
            private fun rename() {
                if (syncingCombo) return
                current.name = field.text
                profileCombo?.repaint()
            }
        })
    }

    private inner class ProfileRenderer : SimpleListCellRenderer<VaultProfile>() {
        override fun customize(list: JList<out VaultProfile>, value: VaultProfile?, index: Int, selected: Boolean, hasFocus: Boolean) {
            if (value == null) return
            val name = value.name.ifBlank { "(unnamed)" }
            text = if (value.id == defaultProfileId) "$name (default)" else name
            toolTipText = value.vaultUrl.ifBlank { null }
        }
    }

    private fun clearSession(profileId: String) {
        VaultCredentialsStore.clear(profileId)
        VaultSessionStatus.clear(profileId)
    }

    // --- Test login ----------------------------------------------------------------------------

    /** Emulates a Vault login with the selected profile's current form values (no need to Apply first). */
    private fun testLogin() {
        // panel.apply() only syncs the widgets -> the working copies; it doesn't touch `settings`
        // (that only happens in this Configurable's apply()), so "Test login" never persists anything.
        panel?.apply()
        val profile = current.copy()

        // Only keep the resulting session if the form matches what is saved for this profile:
        // otherwise a Cancel would leave a token for server B attached to a profile that still
        // points at server A.
        val saved = appSettings.findProfile(profile.id)
        val canKeepSession = saved != null && !profile.invalidatesSessionOf(saved)

        object : Task.Backgroundable(null, "VaultLink: testing login...", true) {
            private var result: Result<AuthResult>? = null

            override fun run(indicator: ProgressIndicator) {
                result = runCatching {
                    val httpClient = HttpClient.newBuilder()
                        .sslContext(CustomTlsSocketFactory.buildSslContext(profile.customCaCertPath))
                        .build()
                    val strategy = AuthStrategyFactory.create(
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
                    strategy.authenticate()
                }
            }

            // IntelliJ calls onSuccess() on the EDT after run() — this (and only this) is where
            // it's safe to touch Swing (statusPanel) and update VaultSessionStatus/VaultCredentialsStore.
            override fun onSuccess() {
                result?.getOrNull()?.let { auth ->
                    if (canKeepSession) {
                        VaultCredentialsStore.store(profile.id, auth.clientToken)
                        VaultSessionStatus.update(profile.id, profile.authMethod, auth)
                        statusPanel?.refresh()
                    }
                }
                LoginNotifier.notifyResult(null, profile.name, profile.authMethod, result ?: Result.failure(IllegalStateException()))
            }
        }.queue()
    }
}

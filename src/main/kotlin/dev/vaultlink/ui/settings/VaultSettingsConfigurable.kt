package dev.vaultlink.ui.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogPanel
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
import dev.vaultlink.ui.common.LoginNotifier
import dev.vaultlink.ui.common.VaultSessionStatusPanel
import java.awt.event.ItemEvent
import java.net.http.HttpClient
import javax.swing.JComponent

/** Settings > Tools > VaultLink — parentId="tools", global config (the Vault server is global). */
class VaultSettingsConfigurable : Configurable {

    private val settings get() = VaultApplicationSettingsService.getInstance().state

    // Editable fields via Kotlin UI DSL v2. The DialogPanel (see `panel` below) is what
    // actually syncs these values with the Swing widgets in apply()/reset().
    private var vaultUrl = ""
    private var namespace = ""
    private var authMethod = AuthMethod.OIDC
    private var oidcMountPath = ""
    private var oidcRole = ""
    private var ldapMountPath = ""
    private var userpassMountPath = ""
    private var callbackPort = 8250
    private var customCaCertPath = ""
    private var envApplyStrategy = EnvApplyStrategy.AUTO

    private var panel: DialogPanel? = null
    private var statusPanel: VaultSessionStatusPanel? = null

    override fun getDisplayName(): String = "VaultLink"

    override fun createComponent(): JComponent {
        loadFromSettings()

        lateinit var oidcMountRow: Row
        lateinit var oidcRoleRow: Row
        lateinit var callbackPortRow: Row
        lateinit var ldapMountRow: Row
        lateinit var userpassMountRow: Row

        fun updateFieldVisibility(method: AuthMethod) {
            oidcMountRow.visible(method == AuthMethod.OIDC)
            oidcRoleRow.visible(method == AuthMethod.OIDC)
            callbackPortRow.visible(method == AuthMethod.OIDC)
            ldapMountRow.visible(method == AuthMethod.LDAP)
            userpassMountRow.visible(method == AuthMethod.USERPASS)
        }

        val statusComponent = VaultSessionStatusPanel()
        statusPanel = statusComponent

        val dialogPanel = panel {
            row {
                cell(statusComponent)
            }
            separator()

            group("Vault Server") {
                row("Vault URL:") { textField().bindText(::vaultUrl) }
                row("Namespace:") { textField().bindText(::namespace) }
                row("Custom CA (PEM/JKS):") {
                    textFieldWithBrowseButton().bindText(::customCaCertPath)
                }.rowComment("Optional — only if Vault uses an internal CA not recognized by the system.")
            }

            group("Authentication") {
                row("Auth method:") {
                    val combo = comboBox(AuthMethod.entries)
                        .bindItem({ authMethod }, { authMethod = it ?: AuthMethod.OIDC })
                        .component
                    combo.addItemListener { event ->
                        if (event.stateChange == ItemEvent.SELECTED) {
                            updateFieldVisibility(event.item as AuthMethod)
                        }
                    }
                }
                oidcMountRow = row("OIDC mount:") { textField().bindText(::oidcMountPath) }
                oidcRoleRow = row("OIDC role:") { textField().bindText(::oidcRole) }
                callbackPortRow = row("Callback port (OIDC):") { intTextField().bindIntText(::callbackPort) }
                ldapMountRow = row("LDAP mount:") { textField().bindText(::ldapMountPath) }
                userpassMountRow = row("Userpass mount:") { textField().bindText(::userpassMountPath) }
                row {
                    button("Test login") { testLogin() }
                }.rowComment("Emulates login with the values from this form (no need to Apply first).")
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
        }
        dialogPanel.border = JBUI.Borders.empty(12)

        updateFieldVisibility(authMethod)
        panel = dialogPanel
        return dialogPanel
    }

    override fun isModified(): Boolean = panel?.isModified() ?: false

    override fun apply() {
        panel?.apply()
        settings.vaultUrl = vaultUrl
        settings.namespace = namespace.ifBlank { null }
        settings.authMethod = authMethod
        settings.oidcMountPath = oidcMountPath
        settings.oidcRole = oidcRole
        settings.ldapMountPath = ldapMountPath
        settings.userpassMountPath = userpassMountPath
        settings.callbackPort = callbackPort
        settings.customCaCertPath = customCaCertPath.ifBlank { null }
        settings.envApplyStrategy = envApplyStrategy
    }

    override fun reset() {
        loadFromSettings()
        panel?.reset()
        statusPanel?.refresh()
    }

    private fun loadFromSettings() {
        vaultUrl = settings.vaultUrl
        namespace = settings.namespace.orEmpty()
        authMethod = settings.authMethod
        oidcMountPath = settings.oidcMountPath
        oidcRole = settings.oidcRole
        ldapMountPath = settings.ldapMountPath
        userpassMountPath = settings.userpassMountPath
        callbackPort = settings.callbackPort
        customCaCertPath = settings.customCaCertPath.orEmpty()
        envApplyStrategy = settings.envApplyStrategy
    }

    /** Emulates a Vault login with the current form values (no need to Apply first). */
    private fun testLogin() {
        // panel.apply() only syncs the widgets -> the properties below; it doesn't touch
        // `settings` (that only happens in this Configurable's apply()), so "Test login"
        // never persists anything on its own.
        panel?.apply()

        val currentVaultUrl = vaultUrl
        val currentAuthMethod = authMethod
        val currentOidcMountPath = oidcMountPath
        val currentOidcRole = oidcRole
        val currentCallbackPort = callbackPort
        val currentCallbackPath = settings.callbackPath
        val currentLdapMountPath = ldapMountPath
        val currentUserpassMountPath = userpassMountPath
        val currentCaCertPath = customCaCertPath.ifBlank { null }

        object : Task.Backgroundable(null, "VaultLink: testing login...", true) {
            private var result: Result<AuthResult>? = null

            override fun run(indicator: ProgressIndicator) {
                result = runCatching {
                    val httpClient = HttpClient.newBuilder()
                        .sslContext(CustomTlsSocketFactory.buildSslContext(currentCaCertPath))
                        .build()
                    val strategy = AuthStrategyFactory.create(
                        authMethod = currentAuthMethod,
                        vaultUrl = currentVaultUrl,
                        oidcMountPath = currentOidcMountPath,
                        oidcRole = currentOidcRole,
                        callbackPort = currentCallbackPort,
                        callbackPath = currentCallbackPath,
                        ldapMountPath = currentLdapMountPath,
                        userpassMountPath = currentUserpassMountPath,
                        httpClient = httpClient,
                    )
                    strategy.authenticate()
                }
            }

            // IntelliJ calls onSuccess() on the EDT after run() — this (and only this) is where
            // it's safe to touch Swing (statusPanel) and update VaultSessionStatus/VaultCredentialsStore.
            override fun onSuccess() {
                result?.getOrNull()?.let { auth ->
                    VaultCredentialsStore.store(auth.clientToken)
                    VaultSessionStatus.update(currentAuthMethod, auth)
                    statusPanel?.refresh()
                }
                LoginNotifier.notifyResult(null, currentAuthMethod, result ?: Result.failure(IllegalStateException()))
            }
        }.queue()
    }
}

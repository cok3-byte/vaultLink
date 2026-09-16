package dev.vaultlink.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SimpleListCellRenderer
import dev.vaultlink.core.vault.model.VaultSecretVersion
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JScrollPane

sealed class VersionChoice {
    object Latest : VersionChoice()
    data class Specific(val version: Int) : VersionChoice()
}

/** Lets the user pin a specific KV v2 version, or pick "Latest" to go back to the default. */
class SecretVersionPickerDialog(
    currentVersion: Int,
    versions: List<VaultSecretVersion>,
) : DialogWrapper(true) {

    private data class LatestEntry(val currentVersion: Int)

    private val listModel = DefaultListModel<Any>().apply {
        addElement(LatestEntry(currentVersion))
        versions.sortedByDescending { it.version }.forEach(::addElement)
    }
    private val list = JList(listModel).apply {
        cellRenderer = object : SimpleListCellRenderer<Any>() {
            override fun customize(list: JList<out Any>, value: Any, index: Int, selected: Boolean, hasFocus: Boolean) {
                text = describe(value)
                icon = if (value is LatestEntry) AllIcons.Actions.Checked else AllIcons.Vcs.History
            }
        }
        selectedIndex = 0
    }

    init {
        title = "Choose Secret Version"
        init()
    }

    override fun createCenterPanel(): JComponent = JScrollPane(list)

    private fun selectedChoice(): VersionChoice? = when (val selected = list.selectedValue) {
        is LatestEntry -> VersionChoice.Latest
        is VaultSecretVersion -> VersionChoice.Specific(selected.version)
        else -> null
    }

    private fun describe(value: Any): String = when (value) {
        is LatestEntry -> "Latest (currently v${value.currentVersion})"
        is VaultSecretVersion -> buildString {
            append("v${value.version}")
            if (value.createdTime.isNotBlank()) append(" — created ${value.createdTime}")
            if (value.destroyed) {
                append(" (destroyed)")
            } else if (!value.deletionTime.isNullOrBlank()) {
                append(" (deleted)")
            }
        }
        else -> value.toString()
    }

    companion object {
        /** Returns the user's choice, or null if the dialog was cancelled (no change). */
        fun pick(currentVersion: Int, versions: List<VaultSecretVersion>): VersionChoice? {
            val dialog = SecretVersionPickerDialog(currentVersion, versions)
            return if (dialog.showAndGet()) dialog.selectedChoice() else null
        }
    }
}

package dev.vaultlink.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SimpleListCellRenderer
import dev.vaultlink.core.vault.model.SecretPath
import dev.vaultlink.core.vault.model.VaultMount
import dev.vaultlink.services.VaultProjectService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel

private sealed class Entry {
    data class Mount(val mount: VaultMount) : Entry()
    data class Folder(val key: String) : Entry() // raw LIST key, ends with "/"
    data class Secret(val key: String) : Entry() // raw LIST key, leaf
}

/**
 * Lets the user browse the configured Vault server's KV v2 mounts and secrets (instead of relying
 * on the `<mount>.<secret>` project-name convention). Each navigation step (list mounts, list
 * secrets/folders, drill into a folder) does a short blocking Vault call under a modal progress
 * dialog — same "sync call from a UI interaction" idiom used elsewhere in this plugin via
 * `Task.Backgroundable`, just synchronous since the dialog needs the result before it can proceed.
 */
class MountSecretPickerDialog private constructor(
    private val project: Project,
    private val service: VaultProjectService,
) : DialogWrapper(project, true) {

    private var mount: String? = null
    private var path: String = "" // segments joined with "/", "" = mount root
    private var selected: SecretPath? = null

    private val breadcrumb = JLabel()
    private val listModel = DefaultListModel<Entry>()
    private val list = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : SimpleListCellRenderer<Entry>() {
            override fun customize(list: JList<out Entry>, value: Entry, index: Int, selected: Boolean, hasFocus: Boolean) {
                text = describe(value)
                icon = iconFor(value)
            }
        }
    }
    private val upButton = JButton("Up")

    init {
        title = "Select Vault Mount and Secret"
        upButton.addActionListener { goUp() }
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) openSelected()
            }
        })
        list.addListSelectionListener { updateOkAction() }
        init()
        loadMounts()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        val topBar = JPanel(BorderLayout()).apply {
            add(breadcrumb, BorderLayout.CENTER)
            add(upButton, BorderLayout.EAST)
        }
        panel.add(topBar, BorderLayout.NORTH)
        panel.add(JScrollPane(list), BorderLayout.CENTER)
        panel.preferredSize = Dimension(480, 360)
        return panel
    }

    override fun doOKAction() {
        val entry = list.selectedValue
        val currentMount = mount
        if (entry is Entry.Secret && currentMount != null) {
            selected = SecretPath(currentMount, path + entry.key)
            super.doOKAction()
        }
    }

    private fun updateOkAction() {
        isOKActionEnabled = list.selectedValue is Entry.Secret
    }

    private fun describe(entry: Entry): String = when (entry) {
        is Entry.Mount -> "${entry.mount.path}/"
        is Entry.Folder -> entry.key
        is Entry.Secret -> entry.key
    }

    private fun iconFor(entry: Entry) = when (entry) {
        is Entry.Mount -> AllIcons.Nodes.PpLibFolder
        is Entry.Folder -> AllIcons.Nodes.Folder
        is Entry.Secret -> AllIcons.Nodes.Module
    }

    private fun openSelected() {
        when (val entry = list.selectedValue) {
            is Entry.Mount -> {
                mount = entry.mount.path
                path = ""
                loadSecrets()
            }
            is Entry.Folder -> {
                path += entry.key
                loadSecrets()
            }
            is Entry.Secret -> doOKAction()
            null -> Unit
        }
    }

    private fun goUp() {
        if (mount == null) return
        if (path.isEmpty()) {
            mount = null
            loadMounts()
            return
        }
        val trimmed = path.removeSuffix("/")
        val lastSlash = trimmed.lastIndexOf('/')
        path = if (lastSlash < 0) "" else trimmed.substring(0, lastSlash + 1)
        loadSecrets()
    }

    private fun loadMounts() {
        breadcrumb.text = "Mounts"
        upButton.isEnabled = false
        runFetch("Listing Vault mounts...") { service.listMounts() }?.let { mounts ->
            listModel.clear()
            mounts.sortedBy { it.path }.forEach { listModel.addElement(Entry.Mount(it)) }
        }
        updateOkAction()
    }

    private fun loadSecrets() {
        val currentMount = mount ?: return
        breadcrumb.text = "$currentMount:/$path"
        upButton.isEnabled = true
        runFetch("Listing secrets in $currentMount:/$path...") { service.listSecrets(currentMount, path) }?.let { keys ->
            listModel.clear()
            keys.sorted().forEach { key ->
                listModel.addElement(if (key.endsWith("/")) Entry.Folder(key) else Entry.Secret(key))
            }
        }
        updateOkAction()
    }

    /** Runs [action] under a modal progress dialog; shows an error dialog and returns null on failure. */
    private fun <T> runFetch(title: String, action: () -> T): T? = try {
        ProgressManager.getInstance().runProcessWithProgressSynchronously<T, Exception>(
            { action() },
            title,
            true,
            project,
        )
    } catch (e: Exception) {
        Messages.showErrorDialog(project, e.message ?: "Vault request failed", "VaultLink")
        null
    }

    companion object {
        /** Returns the picked mount/secret, or null if the dialog was cancelled. */
        fun pick(project: Project, service: VaultProjectService): SecretPath? {
            val dialog = MountSecretPickerDialog(project, service)
            return if (dialog.showAndGet()) dialog.selected else null
        }
    }
}

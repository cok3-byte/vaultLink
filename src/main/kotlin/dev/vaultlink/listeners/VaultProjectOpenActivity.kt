package dev.vaultlink.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.vaultlink.services.VaultProjectService

/** postStartupActivity: detects the <mount>.<secret> pattern in the project name when it opens. */
class VaultProjectOpenActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = project.getService(VaultProjectService::class.java)
        service.resolveSecretPath() ?: return
        // TODO: notify (NotificationGroup "VaultLink") that the pattern was detected and offer
        // to trigger the login/fetch, instead of doing it automatically when the project opens.
    }
}

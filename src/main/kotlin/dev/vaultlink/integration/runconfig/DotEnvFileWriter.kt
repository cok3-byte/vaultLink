package dev.vaultlink.integration.runconfig

import com.intellij.openapi.vfs.LocalFileSystem
import dev.vaultlink.core.util.GitignoreUtil
import java.io.File

/** Universal fallback (Node/Python/Docker or no JVM run config): the only place a secret touches disk. */
object DotEnvFileWriter {
    private const val FILE_NAME = ".env"

    fun write(projectRoot: File, secret: Map<String, String>) {
        val file = File(projectRoot, FILE_NAME)
        val existing = if (file.exists()) parse(file.readText()) else emptyMap()
        val merged = existing + secret
        file.writeText(merged.entries.joinToString(System.lineSeparator()) { (k, v) -> "$k=$v" })
        // file.writeText uses java.io directly, bypassing IntelliJ's VFS — without this refresh
        // the Project view/editor can keep showing stale (or empty) cached content.
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        GitignoreUtil.ensureEntry(projectRoot, FILE_NAME)
    }

    private fun parse(content: String): Map<String, String> =
        content.lineSequence()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx < 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()
}

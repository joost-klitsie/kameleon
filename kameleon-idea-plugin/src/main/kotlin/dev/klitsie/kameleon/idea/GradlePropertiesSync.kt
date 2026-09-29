package dev.klitsie.kameleon.idea

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

class GradlePropertiesSync(private val project: Project) {

    private val log = Logger.getInstance(GradlePropertiesSync::class.java)

    fun updateFlavorOnDisk(dimension: String, flavor: String) {
        val projectDir = project.guessProjectDir()
        if (projectDir == null) {
            log.warn("Cannot locate project directory to sync gradle.properties")
            return
        }

        val targetKey = if (dimension.isBlank() || dimension.equals("default", ignoreCase = true)) "kameleon.flavor" else "kameleon.flavor.$dimension"
        val targetLine = "$targetKey=$flavor"

        val propertiesFile = projectDir.findChild("gradle.properties")
        if (propertiesFile == null || !propertiesFile.isValid) {
            log.info("gradle.properties does not exist. Skipping file write for property '$targetKey'.")
            return
        }

        try {
            val content = VfsUtil.loadText(propertiesFile)
            val lines = content.lines()
            val lineRegex = Regex("""^\s*${Regex.escape(targetKey)}\s*=\s*(.*?)\s*$""")
            val keyIndex = lines.indexOfFirst { lineRegex.matches(it) }

            if (keyIndex != -1) {
                val currentMatch = lineRegex.matchEntire(lines[keyIndex])
                val currentValue = currentMatch?.groupValues?.get(1)
                if (currentValue == flavor) {
                    // Strict idempotency: already has exact requested value, exit immediately!
                    return
                }

                val lineSeparator = if (content.contains("\r\n")) "\r\n" else "\n"
                val updatedLines = lines.toMutableList()
                updatedLines[keyIndex] = targetLine
                val newContent = updatedLines.joinToString(lineSeparator)
                saveContent(propertiesFile, newContent)
            } else {
                log.info("Property '$targetKey' is not defined in gradle.properties. Skipping file write.")
            }
        } catch (e: Throwable) {
            log.error("Failed to update gradle.properties for dimension '$dimension' with flavor '$flavor'", e)
        }
    }

    fun readFlavorFromDisk(dimension: String = "default"): String? {
        val projectDir = project.guessProjectDir() ?: return null
        val propertiesFile = projectDir.findChild("gradle.properties") ?: return null
        if (!propertiesFile.isValid) return null

        return try {
            val content = VfsUtil.loadText(propertiesFile)
            val lines = content.lines()

            val targetKey = if (dimension.isBlank() || dimension.equals("default", ignoreCase = true)) "kameleon.flavor" else "kameleon.flavor.$dimension"
            val lineRegex = Regex("""^\s*${Regex.escape(targetKey)}\s*=\s*(.*?)\s*$""")
            val match = lines.firstNotNullOfOrNull { lineRegex.matchEntire(it) }
            if (match != null) {
                match.groupValues[1].takeIf { it.isNotBlank() }
            } else if (!dimension.equals("default", ignoreCase = true) && dimension.isNotBlank()) {
                val fallbackRegex = Regex("""^\s*kameleon\.flavor\s*=\s*(.*?)\s*$""")
                val fallbackMatch = lines.firstNotNullOfOrNull { fallbackRegex.matchEntire(it) }
                fallbackMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Throwable) {
            log.error("Failed to read flavor from gradle.properties for dimension '$dimension'", e)
            null
        }
    }

    private fun saveContent(file: VirtualFile, newContent: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(file, newContent)
        }
        file.refresh(false, false)
    }
}

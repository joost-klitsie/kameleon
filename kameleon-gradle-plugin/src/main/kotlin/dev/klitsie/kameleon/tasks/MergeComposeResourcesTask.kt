package dev.klitsie.kameleon.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@CacheableTask
abstract class MergeComposeResourcesTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val baseDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val overlayDirs: ListProperty<Directory>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun merge() {
        val outDir = outputDir.get().asFile
        if (outDir.exists()) {
            outDir.deleteRecursively()
        }
        outDir.mkdirs()

        val baseDirectory = baseDir.orNull?.asFile
        if (baseDirectory != null && baseDirectory.exists()) {
            baseDirectory.copyRecursively(outDir, overwrite = true)
        }

        val overlays = overlayDirs.orNull ?: emptyList()
        for (overlayDir in overlays) {
            val dir = overlayDir.asFile
            if (dir.exists()) {
                dir.walkTopDown().forEach { flavorFile ->
                    if (flavorFile.isFile) {
                        val relPath = flavorFile.relativeTo(dir).path
                        val normalizedRelPath = relPath.replace('\\', '/')
                        val targetFile = File(outDir, relPath)

                        if (isValuesStringsXml(normalizedRelPath)) {
                            if (targetFile.exists()) {
                                mergeStringsXml(baseFile = targetFile, flavorFile = flavorFile, outputFile = targetFile)
                            } else {
                                targetFile.parentFile.mkdirs()
                                flavorFile.copyTo(targetFile, overwrite = true)
                            }
                        } else {
                            targetFile.parentFile.mkdirs()
                            flavorFile.copyTo(targetFile, overwrite = true)
                        }
                    }
                }
            }
        }
    }

    private fun isValuesStringsXml(relPath: String): Boolean {
        return relPath.matches(Regex("""^values(-[^/]*)?/strings\.xml$"""))
    }

    private fun mergeStringsXml(baseFile: File, flavorFile: File, outputFile: File) {
        val dbFactory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isIgnoringComments = false
        }
        val dBuilder = dbFactory.newDocumentBuilder()

        val baseDoc = dBuilder.parse(baseFile)
        val flavorDoc = dBuilder.parse(flavorFile)

        baseDoc.documentElement.normalize()
        flavorDoc.documentElement.normalize()

        val baseResources = baseDoc.documentElement
        val flavorResources = flavorDoc.documentElement

        val baseStrings = mutableMapOf<String, Element>()
        val baseChildren = baseResources.childNodes
        for (i in 0 until baseChildren.length) {
            val node = baseChildren.item(i)
            if (node is Element && node.tagName == "string" && node.hasAttribute("name")) {
                baseStrings[node.getAttribute("name")] = node
            }
        }

        val flavorChildren = flavorResources.childNodes
        for (i in 0 until flavorChildren.length) {
            val node = flavorChildren.item(i)
            if (node is Element && node.tagName == "string" && node.hasAttribute("name")) {
                val name = node.getAttribute("name")
                val importedNode = baseDoc.importNode(node, true) as Element
                val existingBaseElement = baseStrings[name]
                if (existingBaseElement != null) {
                    baseResources.replaceChild(importedNode, existingBaseElement)
                    baseStrings[name] = importedNode
                } else {
                    baseResources.appendChild(importedNode)
                    baseStrings[name] = importedNode
                }
            }
        }

        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        }

        outputFile.parentFile.mkdirs()
        outputFile.outputStream().use { out ->
            transformer.transform(DOMSource(baseDoc), StreamResult(out))
        }
    }
}

package dev.klitsie.kameleon.tasks

import dev.klitsie.kameleon.dsl.HoconEntry
import dev.klitsie.kameleon.dsl.HoconSerializer
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class GenerateHoconConfigTask : DefaultTask() {

    @get:Input
    abstract val fileName: Property<String>

    @get:Input
    abstract val entries: ListProperty<HoconEntry>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outDir = outputDirectory.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        val name = fileName.get().ifBlank { "kameleon.conf" }
        val confFile = File(outDir, name)

        val entryList = entries.get()
        val content = HoconSerializer.serialize(entryList)
        confFile.writeText(content)
    }
}

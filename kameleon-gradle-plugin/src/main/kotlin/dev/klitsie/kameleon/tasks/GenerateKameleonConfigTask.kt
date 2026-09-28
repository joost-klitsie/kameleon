package dev.klitsie.kameleon.tasks

import dev.klitsie.kameleon.dsl.BuildConfigValue
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class GenerateKameleonConfigTask : DefaultTask() {

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val className: Property<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val flavorParts: ListProperty<String>

    @get:Input
    abstract val fields: MapProperty<String, BuildConfigValue>

    @get:Input
    @get:org.gradle.api.tasks.Optional
    abstract val language: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outDir = outputDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        val pkg = packageName.get().trim()
        val cls = className.get().trim()
        val variant = variantName.get()
        val flavors = flavorParts.get()
        val primaryFlavor = flavors.firstOrNull() ?: variant

        val fieldMap = fields.get()

        val packageDir = if (pkg.isNotEmpty()) {
            File(outDir, pkg.replace('.', File.separatorChar))
        } else {
            outDir
        }
        packageDir.mkdirs()

        val isJava = language.orNull?.equals("java", ignoreCase = true) == true
        val file = File(packageDir, if (isJava) "$cls.java" else "$cls.kt")

        val convertedFields = mutableMapOf<String, BuildConfigValue>()
        for ((key, value) in fieldMap) {
            val formattedKey = toScreamingSnakeCase(key)
            if (formattedKey != "VARIANT" && formattedKey != "FLAVOR") {
                convertedFields[formattedKey] = value
            }
        }

        val sb = StringBuilder()
        if (isJava) {
            if (pkg.isNotEmpty()) {
                sb.append("package ").append(pkg).append(";\n\n")
            }
            sb.append("public final class ").append(cls).append(" {\n")
            sb.append("    private ").append(cls).append("() {}\n\n")
            sb.append("    public static final String VARIANT = \"").append(escapeJavaString(variant)).append("\";\n")
            sb.append("    public static final String FLAVOR = \"").append(escapeJavaString(primaryFlavor)).append("\";\n")

            for ((key, value) in convertedFields.toSortedMap()) {
                when (value) {
                    is BuildConfigValue.StringValue -> {
                        sb.append("    public static final String ").append(key).append(" = \"")
                            .append(escapeJavaString(value.value)).append("\";\n")
                    }
                    is BuildConfigValue.BooleanValue -> {
                        sb.append("    public static final boolean ").append(key).append(" = ")
                            .append(value.value).append(";\n")
                    }
                    is BuildConfigValue.IntValue -> {
                        sb.append("    public static final int ").append(key).append(" = ")
                            .append(value.value).append(";\n")
                    }
                    is BuildConfigValue.LongValue -> {
                        sb.append("    public static final long ").append(key).append(" = ")
                            .append(value.value).append("L;\n")
                    }
                }
            }
            sb.append("}\n")
        } else {
            if (pkg.isNotEmpty()) {
                sb.append("package ").append(pkg).append("\n\n")
            }

            sb.append("public object ").append(cls).append(" {\n")
            sb.append("    public const val VARIANT: String = \"").append(escapeKotlinString(variant)).append("\"\n")
            sb.append("    public const val FLAVOR: String = \"").append(escapeKotlinString(primaryFlavor)).append("\"\n")

            for ((key, value) in convertedFields.toSortedMap()) {
                when (value) {
                    is BuildConfigValue.StringValue -> {
                        sb.append("    public const val ").append(key).append(": String = \"")
                            .append(escapeKotlinString(value.value)).append("\"\n")
                    }
                    is BuildConfigValue.BooleanValue -> {
                        sb.append("    public const val ").append(key).append(": Boolean = ")
                            .append(value.value).append("\n")
                    }
                    is BuildConfigValue.IntValue -> {
                        sb.append("    public const val ").append(key).append(": Int = ")
                            .append(value.value).append("\n")
                    }
                    is BuildConfigValue.LongValue -> {
                        sb.append("    public const val ").append(key).append(": Long = ")
                            .append(value.value).append("L\n")
                    }
                }
            }
            sb.append("}\n")
        }

        file.writeText(sb.toString())
    }

    private fun escapeKotlinString(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("$", "\${'$'}")
    }

    private fun escapeJavaString(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        fun toScreamingSnakeCase(input: String): String {
            if (input.isEmpty()) return input
            val result = StringBuilder()
            for (i in input.indices) {
                val c = input[i]
                if (c == '_' || c == '-' || c == '.' || c == ' ') {
                    result.append('_')
                } else if (c.isUpperCase()) {
                    if (i > 0 && (input[i - 1].isLowerCase() || (i < input.length - 1 && input[i + 1].isLowerCase()))) {
                        if (result.isNotEmpty() && result.last() != '_') {
                            result.append('_')
                        }
                    }
                    result.append(c)
                } else {
                    result.append(c.uppercaseChar())
                }
            }
            return result.toString().replace(Regex("_+"), "_").trim('_')
        }
    }
}

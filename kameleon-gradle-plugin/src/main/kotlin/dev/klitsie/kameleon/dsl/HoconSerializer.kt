package dev.klitsie.kameleon.dsl

object HoconSerializer {
    fun serialize(entries: List<HoconEntry>): String {
        if (entries.isEmpty()) return ""
        val lines = mutableListOf<String>()
        for (entry in entries) {
            val formattedValue = when (val value = entry.value) {
                is HoconValue.StringValue -> "\"${escapeHoconString(value.value)}\""
                is HoconValue.IntegerValue -> value.value.toString()
                is HoconValue.BooleanValue -> value.value.toString()
                is HoconValue.RawValue -> value.value
            }
            lines.add("${entry.path} = $formattedValue")
            val env = entry.environmentOverride
            if (!env.isNullOrBlank()) {
                lines.add("${entry.path} = \${?$env}")
            }
        }
        return lines.joinToString("\n") + "\n"
    }

    fun escapeHoconString(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}

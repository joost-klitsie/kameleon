package dev.klitsie.kameleon

import dev.klitsie.kameleon.dsl.HoconEntry
import dev.klitsie.kameleon.dsl.HoconSerializer
import dev.klitsie.kameleon.dsl.HoconValue
import kotlin.test.Test
import kotlin.test.assertEquals

class HoconSerializerTest {

    @Test
    fun `serialize empty list returns empty string`() {
        val result = HoconSerializer.serialize(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `serialize formats string, int, boolean and raw values correctly with and without environment overrides`() {
        val entries = listOf(
            HoconEntry(
                path = "server.host",
                value = HoconValue.StringValue("127.0.0.1"),
                environmentOverride = "SERVER_HOST"
            ),
            HoconEntry(
                path = "server.port",
                value = HoconValue.IntegerValue(8080),
                environmentOverride = "SERVER_PORT"
            ),
            HoconEntry(
                path = "server.debug",
                value = HoconValue.BooleanValue(true),
                environmentOverride = null
            ),
            HoconEntry(
                path = "server.allowedHosts",
                value = HoconValue.RawValue("[\"localhost\", \"127.0.0.1\"]"),
                environmentOverride = null
            )
        )

        val result = HoconSerializer.serialize(entries)

        val expected = """
            server.host = "127.0.0.1"
            server.host = ${'$'}{?SERVER_HOST}
            server.port = 8080
            server.port = ${'$'}{?SERVER_PORT}
            server.debug = true
            server.allowedHosts = ["localhost", "127.0.0.1"]

        """.trimIndent()

        assertEquals(expected, result)
    }

    @Test
    fun `serialize escapes special characters in strings properly`() {
        val entries = listOf(
            HoconEntry(
                path = "app.quote",
                value = HoconValue.StringValue("He said \"Hello\""),
                environmentOverride = null
            ),
            HoconEntry(
                path = "app.path",
                value = HoconValue.StringValue("C:\\Users\\admin\\app"),
                environmentOverride = null
            ),
            HoconEntry(
                path = "app.multiline",
                value = HoconValue.StringValue("Line1\nLine2\r\tTabbed"),
                environmentOverride = null
            )
        )

        val result = HoconSerializer.serialize(entries)

        val expected = """
            app.quote = "He said \"Hello\""
            app.path = "C:\\Users\\admin\\app"
            app.multiline = "Line1\nLine2\r\tTabbed"

        """.trimIndent()

        assertEquals(expected, result)
    }
}

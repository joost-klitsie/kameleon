package dev.klitsie.kameleon.sample.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testHelloEndpoint() = testApplication {
        application {
            module()
        }
        val response = client.get("/hello")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("world", response.bodyAsText())
    }
}

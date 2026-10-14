package dev.klitsie.kameleon.sample.server

import dev.klitsie.kameleon.KameleonConfig
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val greeting = environment.config.propertyOrNull("app.greeting")?.getString() ?: "world"

    routing {
        get("/hello") {
            call.respondText(greeting)
        }
        get("/marco") {
            call.respondText(KameleonConfig.MARCO)
        }
    }
}

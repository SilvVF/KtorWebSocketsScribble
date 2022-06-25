package com.silvvf

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.websocket.*

fun main() {
    embeddedServer(
        factory =  Netty,
        port = 8080,
        host = "0.0.0.0",
    ) {
        install(CallLogging)
        install(WebSockets)
        install(ContentNegotiation) {
            gson {

            }
        }
    }.start(wait = true)
}


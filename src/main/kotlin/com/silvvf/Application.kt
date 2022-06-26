package com.silvvf

import com.silvvf.routes.createRoomRoute
import com.silvvf.routes.getRoomsRoute
import com.silvvf.session.DrawingSession
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*

val server = DrawingServer()

fun main() {
    embeddedServer(
        factory =  Netty,
        port = 8001,
        host = "0.0.0.0",
    ) {
        install(Sessions) {
            //attach the session to
            cookie<DrawingSession>("SESSION")
        }
        intercept(ApplicationCallPipeline.Features) {
            //executed whenever a client makes a request to the server
            //check if a session already exists
            if (call.sessions.get<DrawingSession>() != null) {
                //make a new session if it does not exist
                val clientId = call.parameters["client_id"] ?: ""
                call.sessions.set(DrawingSession(clientId, generateNonce()))
            }
        }
        install(CallLogging)
        install(WebSockets)
        install(ContentNegotiation) {
            gson {

            }
        }
        install(Routing) {
            createRoomRoute()
            getRoomsRoute()
        }
    }.start(wait = true)
}


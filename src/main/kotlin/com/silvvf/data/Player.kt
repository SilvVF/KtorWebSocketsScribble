package com.silvvf.data

import io.ktor.http.cio.websocket.*
import java.util.UUID

data class Player(
    val username: String,
    //session for the player that we can send data to -> used in the map of players
    //player can change sessions - this value must be swapped for that
    var socket: WebSocketSession,
    val clientId: String,
    var isDrawing: Boolean = false,
    var score: Int = 0,
    //rank in the players current room
    var rank: Int = 0,
)

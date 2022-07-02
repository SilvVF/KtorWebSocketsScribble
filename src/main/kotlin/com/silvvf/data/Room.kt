package com.silvvf.data

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.isActive

class Room(
    //used to find the room
    val name: String,
    var maxPlayers: Int,
    var players: List<Player> = emptyList()
) {
    //used to update the chat for all players in the game
    suspend fun broadcast(message: String) {
        players.forEach {player ->
            if (!player.socket.isActive) return
            player.socket.send(
                Frame.Text(text = message)
            )
        }
    }

    suspend fun broadcastToAllExcept(message: String,vararg clientId: String) {
        players.forEach { player ->
            //broadcast the Frame to all players not in the list
            if (!player.socket.isActive && player.clientId !in clientId) return
            player.socket.send(
                Frame.Text(text = message)
            )
        }
    }

    fun containsPlayer(username: String): Boolean {
        players.forEach {
            if (it.username == username) return true
        }
        return false
    }
}


package com.silvvf

import com.silvvf.data.Player
import com.silvvf.data.Room
import java.util.concurrent.ConcurrentHashMap

class DrawingServer(

) {
    //optimized for concurrent accessing of the hashmap - prevents issues with multi-access
    val rooms = ConcurrentHashMap<String, Room>()
    //key = clientId : value = player
    val players = ConcurrentHashMap<String, Player>()

    fun playerJoined(player: Player) {
        players[player.clientId] = player
    }

    //get the room the player is apart of
    fun getRoomFromClientId(clientId: String): Room? {
        val filteredRooms = rooms.filterValues { room ->
            //filter out rooms that do not contain the player with clientId
            room.players.find { player ->
                player.clientId == clientId
            } != null
        }
        return if(filteredRooms.isNotEmpty()) filteredRooms.values.first() else null
    }
}

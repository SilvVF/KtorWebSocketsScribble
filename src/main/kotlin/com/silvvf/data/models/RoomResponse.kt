package com.silvvf.data.models

data class RoomResponse(
    val name: String,
    val maxPlayers: Int,
    //players curr in the room
    val playerCount: Int,
)

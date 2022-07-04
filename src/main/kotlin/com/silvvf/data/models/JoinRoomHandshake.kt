package com.silvvf.data.models

import com.silvvf.util.Constants.TYPE_JOIN_ROOM_HANDSHAKE
//used when a player joins a room
data class JoinRoomHandshake(
    val username: String,
    val roomName: String,
    val clientId: String,
): BaseModel(TYPE_JOIN_ROOM_HANDSHAKE)

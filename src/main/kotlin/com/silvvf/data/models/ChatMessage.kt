package com.silvvf.data.models

import com.silvvf.util.Constants.TYPE_CHAT_MESSAGE

//represents a single message sent to players in a room
data class ChatMessage(
    val from : String,
    val roomName: String,
    val message: String,
    val timeStamp: Long,
) : BaseModel(type = TYPE_CHAT_MESSAGE)

package com.silvvf.data.models

import com.silvvf.util.Constants

data class Announcement(
    val message: String,
    val timestamp: Long,
    val announcementType: Int,
): BaseModel(Constants.TYPE_ANNOUNCEMENT) {
    companion object {
        const val TYPE_PLAYER_GUESSED_WORD  = 0
        const val TYPE_PLAYER_JOINED  = 1
        const val TYPE_PLAYER_LEFT  = 2
        const val TYPE_EVERYONE_GUESSED_IT  = 3
    }
}

package com.silvvf.data.models

import com.silvvf.util.Constants.TYPE_GAME_ERROR
//only sent by the server doesn't need to be parsed to be handled on the server
data class GameError(
    val errorType: Int,
): BaseModel(TYPE_GAME_ERROR) {
    companion object {
        //respond if room not found in join room request
        const val ERROR_ROOM_NOT_FOUND = 0
    }
}

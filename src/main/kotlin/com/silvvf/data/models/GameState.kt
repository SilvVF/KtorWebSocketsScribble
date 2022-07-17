package com.silvvf.data.models

import com.silvvf.data.Player
import com.silvvf.util.Constants

data class GameState(
    val drawingPlayer: String,
    val chosenWord: String
): BaseModel(Constants.TYPE_GAME_STATE)

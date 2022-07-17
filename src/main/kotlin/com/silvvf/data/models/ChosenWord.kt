package com.silvvf.data.models

import com.silvvf.util.Constants

data class ChosenWord(
    val ChosenWord: String,
    val roomName: String,
): BaseModel(Constants.TYPE_CHOSEN_WORD)

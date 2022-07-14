package com.silvvf.data.models

import com.silvvf.data.Room
import com.silvvf.util.Constants

data class PhaseChange(
    var phase: Room.Phase?,
    var time: Long,
    val drawingPlayer: String? = null
): BaseModel(Constants.TYPE_PHASE_CHANGE)

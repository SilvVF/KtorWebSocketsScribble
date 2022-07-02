package com.silvvf.data

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.isActive

class Room(
    //used to find the room
    val name: String,
    var maxPlayers: Int,
    var players: List<Player> = emptyList()
) {
    init {
        //what to do when the phase setter is accessed handles the change
        setPhaseChangedListener {phase ->
            when (phase) {
                Phase.WAITING_FOR_PLAYERS -> waitingForPlayers()
                Phase.WAITING_FOR_START -> waitingForStart()
                Phase.GAME_RUNNING -> gameRunning()
                Phase.NEW_ROUND -> newRound()
                Phase.SHOW_WORD -> showWord()
            }
        }
    }
    private var phaseChangedListener: ((Phase) -> Unit)? = null
    var phase = Phase.WAITING_FOR_PLAYERS
        set(value) {
            //only one thread at a time can access this setter
            synchronized(field) {
                field = value
                phaseChangedListener?.let {change ->
                    change(value)
                }
            }
        }
    private fun setPhaseChangedListener(listener: (Phase) -> Unit) {
        phaseChangedListener = listener
    }
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
    private fun waitingForPlayers() {

    }
    private fun waitingForStart(){

    }
    private fun newRound(){

    }
    private fun gameRunning(){

    }
    private fun showWord(){

    }
    enum class Phase{
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        GAME_RUNNING,
        SHOW_WORD
    }

}


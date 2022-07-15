package com.silvvf.data

import com.silvvf.data.models.Announcement
import com.silvvf.data.models.ChosenWord
import com.silvvf.data.models.PhaseChange
import com.silvvf.gson
import com.silvvf.util.Constants
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*

class Room(
    //used to find the room
    val name: String,
    var maxPlayers: Int,
    var players: List<Player> = emptyList()
) {
    private var phaseChangedListener: ((Phase) -> Unit)? = null
    private var timerJob: Job? = null
    private var winningPlayer = listOf<Player>()
    private var drawingPlayer: Player? = null
    private var word: String? = null
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

    private fun setPhaseChangedListener(listener: (Phase) -> Unit) {
        phaseChangedListener = listener
    }

    suspend fun addPlayer(clientId: String, username: String, socketSession: WebSocketSession): Player {
        val player = Player(
            username = username,
            socket = socketSession,
            clientId = clientId
        )
        this.players = this.players + player
        if (players.size == 1) {
            phase = Phase.WAITING_FOR_PLAYERS
        }else if(players.size == 2 && phase == Phase.WAITING_FOR_PLAYERS) {
            phase = Phase.WAITING_FOR_START
            //helps randomize the drawing player
            players = players.shuffled()
        }else if (phase == Phase.WAITING_FOR_START && players.size == maxPlayers) {
            //room is full start the game
            phase = Phase.NEW_ROUND
            players = players.shuffled()
        }
        val announcement = Announcement(
            message = "$username has joined the game!",
            timestamp = System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_JOINED
        )
        //send the announcement to other players
        broadcast(gson.toJson(announcement))
        return player
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

    private fun timeAndNotify(ms: Long) {
        timerJob?.cancel()
        drawingPlayer = players.find { it.isDrawing }
        //no lifecycle on a server everything can be global scope
        timerJob = GlobalScope.launch {
            val phaseChange = PhaseChange(
                phase = phase,
                time = ms,
                drawingPlayer = drawingPlayer?.username ?: ""
            )
            //10,000 / 1,000 = 10 update every clients time 10 times
            repeat((ms / UPDATE_FREQ).toInt()) {
                // if this is not the first repeat the phase has not changed
                if (it != 0) { phaseChange.phase = null }
                //broadcasts the phase change at first repeat
                broadcast(gson.toJson(phaseChange))
                //decrease the phases time
                phaseChange.time -= UPDATE_FREQ
                delay(UPDATE_FREQ)
            }
            //set the new phase when the currents' timer is up
            //below is the game flow -> each change triggers the setter of phase
            phase = when (phase) {
                Phase.WAITING_FOR_START -> Phase.NEW_ROUND
                Phase.GAME_RUNNING -> Phase.SHOW_WORD
                Phase.SHOW_WORD -> Phase.NEW_ROUND
                Phase.NEW_ROUND -> Phase.GAME_RUNNING
                else -> Phase.WAITING_FOR_PLAYERS
            }
        }
    }

    fun containsPlayer(username: String): Boolean {
        players.forEach {
            if (it.username == username) return true
        }
        return false
    }
    //called from the WebSocketRoute handles starting the game flow and setting the  word
    fun setWordAndSwitchToGameRunning(word: String) {
        this.word = word
        phase = Phase.GAME_RUNNING
    }
    private fun waitingForPlayers() {
        GlobalScope.launch {
            val phaseChange = PhaseChange(
                phase = Phase.WAITING_FOR_PLAYERS,
                time = DELAY_WAITING_FOR_START_TO_NEWROUND
            )
            broadcast(gson.toJson(phaseChange))
        }
    }
    private fun waitingForStart(){
        GlobalScope.launch {
            timeAndNotify(DELAY_WAITING_FOR_START_TO_NEWROUND)
            val phaseChange = PhaseChange(
                phase = Phase.WAITING_FOR_START,
                time = DELAY_WAITING_FOR_START_TO_NEWROUND
            )
            broadcast(gson.toJson(phaseChange))
        }
    }
    private fun newRound(){

    }
    private fun gameRunning(){

    }
    private fun showWord(){
        GlobalScope.launch {
            drawingPlayer?.let { drawingPlayer ->
                if (winningPlayer.isEmpty()) {
                    //no winning player take out penalty
                    drawingPlayer.score -= Constants.WORD_NOT_GUESSED_PENALTY
                }
            }
            word?.let {
                val chosenWord = ChosenWord(
                    ChosenWord = it,
                    roomName = name
                )
                //broadcast what the word was - type is handled by the wrapper class for passing frames
                broadcast(gson.toJson(chosenWord))
                //how long the word will be shown before a new round
                timeAndNotify(DELAY_SHOW_WORD_TO_NEW_ROUND)
                val phaseChange = PhaseChange(Phase.SHOW_WORD, DELAY_SHOW_WORD_TO_NEW_ROUND)
                broadcast(gson.toJson(phaseChange))
            }
        }
    }
    enum class Phase{
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        GAME_RUNNING,
        SHOW_WORD
    }
    companion object  {

        //how often time updates are sent to the client
        const val UPDATE_FREQ = 1000L
        const val DELAY_WAITING_FOR_START_TO_NEWROUND = 10000L
        const val DELAY_NEWROUND_TO_GAME_RUNNING = 20000L
        const val DELAY_GAME_RUNNING_TO_SHOW_WORD = 500000L
        const val DELAY_SHOW_WORD_TO_NEW_ROUND = 10000L
    }
}


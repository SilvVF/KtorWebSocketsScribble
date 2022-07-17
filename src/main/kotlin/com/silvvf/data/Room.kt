package com.silvvf.data

import com.silvvf.data.models.*
import com.silvvf.gson
import com.silvvf.util.*
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
    private var winningPlayers = listOf<String>()
    private var drawingPlayer: Player? = null
    private var word: String? = null
    private var drawingPlayerIndex = 0
    //will hold a timestamp of time elapsed to award point bonuses for fast times
    private var startTime = 0L
    //contains a list of the words for the drawer to choose from
    // random one is selected if one is not choosen
    private var currWords: List<String>? = null

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

    //check if a guess matches the current drawing players word
    fun isGuessCorrect(guess: ChatMessage): Boolean {
        return guess.matchesWord(word?: return false) &&
                guess.from !in winningPlayers &&
                guess.from != drawingPlayer?.username &&
                phase == Phase.GAME_RUNNING

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
        //saved as a timestamp to calculate time elapsed
        startTime = System.currentTimeMillis()
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
    private fun newRound() {
        //generate three new words
        currWords = getRandomWords(3)
        val newWords = NewWords(currWords ?: getRandomWords(3))
        //update the drawing player
        nextDrawingPlayer()
        GlobalScope.launch {
            //send the new drawing player a list of words
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(newWords)))
        }
    }
    private fun gameRunning(){
        winningPlayers = emptyList()
        //set word or random from the three if the player didnt choose
        val wordToSend = word ?: currWords?.random() ?: words.random()
        val maskedWord = wordToSend.transformToUnderscores()
        val drawingUserName = drawingPlayer?.username ?: players.random().username
            .also { drawingPlayer = players.find { p -> p.username == it } }
        val gameStateDrawingPlayer = GameState(drawingUserName, wordToSend)
        val gameStateGuessingPlayer = GameState(drawingUserName, maskedWord)
        GlobalScope.launch {
            launch {
                //send message with unmasked word to drawing player individually
                drawingPlayer?.socket?.send(Frame.Text(gson.toJson(gameStateDrawingPlayer)))
            }
            launch {
                //send the masked word to all the guessing players
                broadcastToAllExcept(gson.toJson(gameStateGuessingPlayer), drawingPlayer?.clientId ?: players.random().clientId)
            }
                //update the new game state game is now running 
            timeAndNotify(DELAY_GAME_RUNNING_TO_SHOW_WORD)
        }
    }
    private fun showWord(){
        GlobalScope.launch {
            drawingPlayer?.let { drawingPlayer ->
                if (winningPlayers.isEmpty()) {
                    //no winning player take out penalty
                    drawingPlayer.score -= WORD_NOT_GUESSED_PENALTY
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

    //returns true if all the players have guessed the word otherwise false
    private fun addWinningPlayer(playerName: String): Boolean {
        winningPlayers = winningPlayers + playerName
        //need to exclude the drawing player that is in players so - 1
        return when {
            winningPlayers.size == players.size - 1 -> {
                phase = Phase.NEW_ROUND
                true
            }
            else -> false
        }
    }
    //check if a guess was correct award points and announce it
    suspend fun checkWordAndNotifyPlayers(message: ChatMessage): Boolean {
        if (!isGuessCorrect(message)){ return false }
        val guessedTime = System.currentTimeMillis() - startTime
        val timePercentageLeft = 1f - (guessedTime.toFloat() / DELAY_GAME_RUNNING_TO_SHOW_WORD)
        val score = GUESS_SCORE + (GUESS_SCORE_PERCENT_MULTIPLIER * timePercentageLeft)
        val guessingPlayer = players.find{ message.from == it.username } ?: return false
        guessingPlayer.score += score.toInt()
        drawingPlayer?.let {
            it.score += GUESSED_SCORE_FOR_DRAWING_PLAYER / players.size
        }
        val announcement = Announcement(
            message = "${message.from} has guessed the word!",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_GUESSED_WORD
        )
        broadcast(gson.toJson(announcement))
        val isRoundOver = addWinningPlayer(message.from)
        if (isRoundOver) {
            broadcast(gson.toJson(
                Announcement(
                    message = "Round is now over everyone has guessed the word correctly!",
                    System.currentTimeMillis(),
                    Announcement.TYPE_EVERYONE_GUESSED_IT
                )
            ))
        }
        return true
    }
    private fun nextDrawingPlayer() {
        //make sure the room contains players
        if (players.isEmpty()) {return}
        //update the old drawing player to not drawing
        drawingPlayer?.isDrawing = false
        if (drawingPlayerIndex <= players.lastIndex) {
            drawingPlayerIndex += 1
            players[drawingPlayerIndex].isDrawing = true
        } else {
            drawingPlayerIndex = 0
            players.first().isDrawing = true
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

        const val WORD_NOT_GUESSED_PENALTY = 50
        const val GUESS_SCORE = 50
        //quicker the word is guessed more points awarded
        const val GUESS_SCORE_PERCENT_MULTIPLIER = 50
        //awarded to drawing player in full if all players get the word only gets a portion = this value / player count
        const val GUESSED_SCORE_FOR_DRAWING_PLAYER = 50
    }
}


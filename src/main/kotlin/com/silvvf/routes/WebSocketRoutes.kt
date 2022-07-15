package com.silvvf.routes

import com.google.gson.JsonParser
import com.silvvf.data.Player
import com.silvvf.data.Room
import com.silvvf.data.models.*
import com.silvvf.gson
import com.silvvf.server
import com.silvvf.session.DrawingSession
import com.silvvf.util.Constants.TYPE_ANNOUNCEMENT
import com.silvvf.util.Constants.TYPE_CHAT_MESSAGE
import com.silvvf.util.Constants.TYPE_CHOOSEN_WORD
import com.silvvf.util.Constants.TYPE_DRAW_DATA
import com.silvvf.util.Constants.TYPE_JOIN_ROOM_HANDSHAKE
import com.silvvf.util.Constants.TYPE_PHASE_CHANGE
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

fun Route.gameWebSocketRoute() {
    route("/ws/draw") {
        //uses the websocket wrapper created place of normal web socket
        standardWebSocket { socket, clientId, message, payload ->
            when(payload) {
                is DrawData -> {
                    //get the room the player is drawing in
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                    //if the game is in the running state
                    if (room.phase == Room.Phase.GAME_RUNNING) {
                        //broadcast the draw data to all players except the one drawing
                        room.broadcastToAllExcept(message, clientId)
                    }
                }
                is ChatMessage -> {

                }
                is JoinRoomHandshake -> {
                    //find the room
                    val room = server.rooms[payload.roomName]
                    if (room == null) {
                        //if the room does not exist send an error response to client
                        val gameError = GameError(GameError.ERROR_ROOM_NOT_FOUND)
                        socket.send(Frame.Text(gson.toJson(gameError)))
                        return@standardWebSocket
                    }
                    val player = Player(
                        username = payload.username,
                        socket,
                        clientId = payload.clientId
                    )
                    server.playerJoined(player)
                    //check if player with the same name already exists
                    if (!room.containsPlayer(player.username)) {
                        room.addPlayer(player.clientId, player.username, socket)
                    }
                }
                is ChosenWord -> {
                    //get the room from the server return out if it doesnt exist
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                    //handles setting the word and changing the game phase
                    room.setWordAndSwitchToGameRunning(payload.ChosenWord)
                }
            }
        }
    }
}


//wrapper function used inside a route
//function handles parsing the json to gson and getting the
//type of object contained in the frame
fun Route.standardWebSocket(
    //what to do when a frame is received
    handleFrame: suspend (
        //connection from one client to the server -> send to single client
        socket: DefaultWebSocketSession,
        //determine who is the sender
        clientId: String,
        //data being sent -> usually JSON format
        message: String,
        //parsed json data
        payload: BaseModel
    ) -> Unit,
) {
    //receives web socket request instead of post / get
    //refers to default websocket send data to a specific user
    webSocket {
        val session = call.sessions.get<DrawingSession>()
        //handle what happens if no session is found
        if (session == null) {
            close(CloseReason(
                    code = CloseReason.Codes.VIOLATED_POLICY.code,
                    message = "No Session was found."
            ))
            return@webSocket
        }
        //use try catch because error will be thrown when a client
        //disconnects
        try {
            //Channel<Frame> -> can be consumed to handle event
            //suspend fun - lifecycle: as long as connection is open
            incoming.consumeEach {frame ->
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    //convert the string to json
                    val jsonObj = JsonParser.parseString(message).asJsonObject
                    //extract the type from the json object -> inherits from BaseModel
                    val type = when(jsonObj.get("type").asString) {
                        TYPE_CHAT_MESSAGE -> ChatMessage::class.java
                        TYPE_DRAW_DATA -> DrawData::class.java
                        TYPE_ANNOUNCEMENT -> Announcement::class.java
                        TYPE_JOIN_ROOM_HANDSHAKE -> JoinRoomHandshake::class.java
                        TYPE_PHASE_CHANGE -> PhaseChange::class.java
                        TYPE_CHOOSEN_WORD -> ChosenWord::class.java
                        else -> BaseModel::class.java //should never happen
                    }
                    //convert the frame and json to a gson object
                    val payload = gson.fromJson(message, type)
                    handleFrame(this, session.clientId, message, payload)
                }
            }
        }catch (e: Exception) {
            e.printStackTrace()
        }finally {
            //TODO - HANDLE DISCONNECTS
        }
    }
}

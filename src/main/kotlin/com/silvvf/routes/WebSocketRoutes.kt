package com.silvvf.routes

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.silvvf.data.models.BaseModel
import com.silvvf.data.models.ChatMessage
import com.silvvf.gson
import com.silvvf.session.DrawingSession
import com.silvvf.util.Constants
import com.silvvf.util.Constants.TYPE_CHAT_MESSAGE
import io.ktor.gson.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.Identity.decode
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.consumeEach

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

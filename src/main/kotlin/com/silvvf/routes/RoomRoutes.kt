package com.silvvf.routes

import com.silvvf.data.Room
import com.silvvf.data.models.BasicApiResponse
import com.silvvf.data.models.CreateRoomRequest
import com.silvvf.data.models.RoomResponse
import com.silvvf.server
import com.silvvf.util.Constants.MAX_ROOM_SIZE
import com.silvvf.util.Constants.MIN_ROOM_SIZE
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

fun Route.createRoomRoute() {
    route("/api/createRoom") {
        post {
            val request = call.receiveOrNull<CreateRoomRequest>()
            if (request == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            //make sure a room doesn't already exist with the same name
            if (server.rooms.containsKey(request.name)) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(
                        successful = false,
                        message = "A room with that name already exists."
                    )
                )
                return@post
            }
            //don't trust the client verify that the request is valid -> could lead to server crashes
            //each game must have at least 2 players
            if (request.maxPlayers < MIN_ROOM_SIZE) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(
                        successful = false,
                        message = "a room must have at least $MIN_ROOM_SIZE players."
                    )
                )
                return@post
            }
            if (request.maxPlayers > MAX_ROOM_SIZE) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(
                        successful = false,
                        message = "a room can have at most $MAX_ROOM_SIZE players."
                    )
                )
                return@post
            }
            //create a room with the requested name if it passes checks
            val room = Room(
                name = request.name,
                maxPlayers = request.maxPlayers,
            )
            server.rooms[room.name] = room
            println("Room Created: -> name : \"${room.name}\"")
            call.respond(
                status = HttpStatusCode.OK,
                message = BasicApiResponse(successful = true)
            )
        }
    }
}


fun Route.getRoomsRoute() {
    route("/api/getRooms"){
        get {
            val query = call.parameters["searchQuery"]
            if (query == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val rooms = server.rooms.filterKeys { roomName ->
                //filter all rooms that contain the query string
                roomName.contains(query, ignoreCase = true)
            }
            //take all rooms that matched the filter and make room responses
            //sort in alphabetical order by the room name
            val roomResponses = rooms.values.map { room ->
                RoomResponse(
                    name = room.name,
                    maxPlayers = room.maxPlayers,
                    playerCount = room.players.size
                )
            }.sortedBy { room -> room.name }
            //respond to the request with the rooms
            call.respond(
                status = HttpStatusCode.OK,
                message = roomResponses
            )
        }
    }
}

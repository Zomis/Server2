package net.zomis.games.server2.chat

import net.zomis.games.server2.Client

data class ChatMessage(val client: Client, val message: String)
data class ClientJoinedEvent(val client: Client, val room: ChatRoom)
data class ClientLeftEvent(val client: Client, val room: ChatRoom)

data class ChatRoom(val id: String, val name: String) {

}

class ChatSystem {

    private val rooms: MutableMap<String, ChatRoom> = mutableMapOf()

}

package net.zomis.games.server2.invites

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.*
import net.zomis.games.server2.games.Game
import net.zomis.games.server2.games.GameEndedEvent
import net.zomis.games.server2.games.GameStartedEvent

data class ClientInterestingGames(val interestingGames: Set<String>, val maxGames: Int, val currentGames: MutableSet<Game>)
data class ListRequest(val client: Client)

class LobbySystem {

    val clientData = ClientData<ClientInterestingGames>()

    fun register(events: EventSystem) {
        clientData.register(events)
        events.listen("set client interesting games games", ClientJsonMessage::class, {
            it.data.getTextOrDefault("type", "") == "ClientGames"
        }, {
            val gameTypes = (it.data.get("gameTypes") as ArrayNode).map { it.asText() }.toSet()
            val maxGames = it.data.get("maxGames").asInt()
            clientData.setForClient(ClientInterestingGames(gameTypes, maxGames, mutableSetOf()), it.client)
            // set interesting games
            // set max number of concurrent games (defaults to 1, -1 = Infinite)

            // send information to other clients
        })

        // client disconnect: broadcast to all other clients

        events.listen("Lobby mark player as in game", GameStartedEvent::class, {true}, { gameEvent ->
            gameEvent.game.players.map(clientData::get).forEach { it?.currentGames?.add(gameEvent.game) }
        })

        events.listen("Lobby remove player from game", GameEndedEvent::class, {true}, { gameEvent ->
            gameEvent.game.players.map(clientData::get).forEach { it?.currentGames?.remove(gameEvent.game) }
        })

        events.listen("fire ListRequest", ClientJsonMessage::class,
                { it.data.getTextOrDefault("type", "") == "ListRequest" }, {
            events.execute(ListRequest(it.client))
        })

        data class InterestingClient(val client: Client, val gameType: String)
        events.listen("send available users", ListRequest::class, {true}, { event ->
            val interestingGames = clientData.get(event.client)?.interestingGames
            val mapper = ObjectMapper()
            val data = clientData.entries().filter {
                it.value.maxGames > it.value.currentGames.size
            }.flatMap {
                e -> e.value.interestingGames.map { InterestingClient(e.key, it) }
            }.filter { interestingGames?.contains(it.gameType) ?: false }
            .filter { it.client.name != null }
            .filter { it.client != event.client }
            .groupBy({it.gameType}, {it.client.name!!})

            val result = mapper.createObjectNode().put("type", "Lobby")
            val users = mapper.createObjectNode()
            data.forEach {
                // convert to JsonNode. Easy to write, although not most efficient
                val array = mapper.valueToTree<ArrayNode>(it.value)
                users.set(it.key, array)
            }
            result.set("users", users)
            event.client.send(result)
        })
    }

}
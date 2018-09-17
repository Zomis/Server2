package net.zomis.games.server2.ais

import com.fasterxml.jackson.databind.ObjectMapper
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.*
import net.zomis.games.server2.games.Game
import net.zomis.games.server2.games.MoveEvent
import net.zomis.games.server2.games.PlayerGameMoveRequest
import net.zomis.games.server2.invites.InviteEvent
import net.zomis.games.server2.invites.InviteResponseEvent

data class AICreatedEvent(val ai: ServerAI)

class ServerAI(val gameType: String, val name: String, val perform: (Game, Int) -> List<PlayerGameMoveRequest>) {
    fun register(events: EventSystem) {
        events.listen("ai move $name", MoveEvent::class, {
            it.game.players.contains(client)
        }, {
            it.game.players.asSequence().forEachIndexed { index, client ->
                if (client == this.client) {
                    val aiMoves = perform.invoke(it.game, index)
                    aiMoves.forEach {
                        Thread.sleep(1000)
                        events.execute(it)
                    }
                }
            }
        })
        events.listen("ServerAI $gameType accept invite $name", InviteEvent::class, {
            it.targets.contains(client)
        }, {
            events.execute(InviteResponseEvent(client, it.invite, true))
        })

        events.execute(ClientConnected(client))
        client.name = name
        events.execute(ClientLoginEvent(client, name, "server-ai"))
        val mapper = ObjectMapper()
        val interestingGames = mapper.readTree(mapper.writeValueAsString(mapOf("type" to "ClientGames",
            "gameTypes" to listOf(gameType), "maxGames" to 100
        )))
        events.execute(ClientJsonMessage(client, interestingGames))
        events.execute(AICreatedEvent(this))
    }

    val client = Client()




}
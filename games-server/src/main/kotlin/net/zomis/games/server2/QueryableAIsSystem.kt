package net.zomis.games.server2

import net.zomis.core.events.EventSystem
import net.zomis.games.server2.ais.AICreatedEvent
import net.zomis.games.server2.games.GameSystem

class QueryableAIsSystem(gameSystem: GameSystem) {

    fun register(events: EventSystem) {
        events.listen("Create Queryable AI", AICreatedEvent::class, {true}, {aiCreatedEvent ->
            val ai = aiCreatedEvent.ai
            events.listen("Query AI ${ai.name}", ClientJsonMessage::class,
                    {it.data.getTextOrDefault("type", "") == "ai-query" &&
                            it.data.getTextOrDefault("ai", "") == ai.name &&
                            it.data.getTextOrDefault("gametype", "") == ai.name}, {

                // use a ClientJsonGameEvent for all events that contain a gameType + gameId ?
//                val gameId = it.data.getTextOrDefault() // get gameid
//                it.
            })
        })
    }


}

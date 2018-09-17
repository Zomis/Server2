package net.zomis.games.server2

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import klogging.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.server2.ais.ServerAIs
import net.zomis.games.server2.games.GameListSystem
import net.zomis.games.server2.games.GameSystem
import net.zomis.games.server2.games.ObserverSystem
import net.zomis.games.server2.games.SimpleMatchMakingSystem
import net.zomis.games.server2.games.impl.RoyalGameOfUrSystem
import net.zomis.games.server2.games.impl.TTControllerSystem
import net.zomis.games.server2.invites.InviteSystem
import net.zomis.games.server2.invites.LobbySystem
import net.zomis.games.server2.javalin.auth.LinAuth
import net.zomis.games.server2.ws.Server2WS
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.games.TTClassicControllerWithGravity
import net.zomis.tttultimate.games.TTUltimateController
import java.net.InetSocketAddress
import javax.script.ScriptEngineManager

fun JsonNode.getTextOrDefault(fieldName: String, default: String): String {
    return if (this.hasNonNull(fieldName)) this.get(fieldName).asText() else default
}

data class IllegalClientRequest(val client: Client, val error: String)

class ServerConfig {

    @Parameter(names = ["-wsport"], description = "Port number for WebSockets")
    var wsport: Int = 8081

    @Parameter(names = ["-httpPort"], description = "Port number for Authentication REST-server (0 to disable)")
    var httpPort: Int = 0

}

class Server2(val events: EventSystem) {
    private val logger = KLoggers.logger(this)
    private val mapper = ObjectMapper()

    fun start(config: ServerConfig) {
        Runtime.getRuntime().addShutdownHook(Thread({ events.execute(ShutdownEvent("runtime shutdown hook")) }))
        events.listen("Start WebSocket Server", StartupEvent::class, {true}, {
            val ws = Server2WS(events, InetSocketAddress(config.wsport)).setup()
            logger.info("WebSocket server listening at ${ws.port}")
        })

        events.listen("v1: JsonMessage", ClientMessage::class, {it.message.startsWith("v1:")}, {
            events.execute(ClientJsonMessage(it.client, mapper.readTree(it.message.substring("v1:".length))))
        })
        events.listen("JSON Message", ClientMessage::class, {
            it.message.trimStart().startsWith("{")
        }, {
            events.execute(ClientJsonMessage(it.client, mapper.readTree(it.message)))
        })
        val gameSystem = GameSystem(events)

        TTControllerSystem("Connect4", {TTClassicControllerWithGravity(TTFactories().classicMNK(7, 6, 4))}).register(events)
        TTControllerSystem("UTTT", {TTUltimateController(TTFactories().ultimate())}).register(events)
        RoyalGameOfUrSystem.init(events)

        SimpleMatchMakingSystem(gameSystem, events)
        events.with(ServerConsole()::register)
        events.with(ObserverSystem(events, gameSystem)::register)
        events.with(GameListSystem(gameSystem)::register)
        events.with(AuthorizationSystem()::register)
        events.with(ServerAIs()::register)
        events.with(LobbySystem()::register)
        val clientsByName = ClientsByName()
        events.with(clientsByName::register)
        events.with { InviteSystem(gameSystem).register(it, clientsByName) }
        events.with(QueryableAIsSystem(gameSystem)::register)
        if (config.httpPort != 0) {
            events.with(LinAuth(config.httpPort)::register)
        }

        val engine = ScriptEngineManager().getEngineByExtension("kts")!!
        events.listen("Kotlin script", ConsoleEvent::class, {it.input.startsWith("kt ")}, {
            val result = engine.eval(it.input.substring("kt ".length))
            println(result)
        })
        events.execute(StartupEvent(System.currentTimeMillis()))
    }

    fun stop() {
        events.execute(ShutdownEvent("stop called"))
    }

}

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = ServerConfig()
        val cmd = JCommander(config)
        try {
            cmd.parse(*args)
        } catch (e: ParameterException) {
            cmd.usage()
            System.exit(1)
        }

        // TODO: Run with auto-docs to print event chains.
        Server2(EventSystem()).start(config)
    }
}

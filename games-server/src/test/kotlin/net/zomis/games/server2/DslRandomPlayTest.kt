package net.zomis.games.server2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Actionable
import net.zomis.games.impl.SplendorGame
import net.zomis.games.dsl.impl.GameController
import net.zomis.games.dsl.impl.GameControllerContext
import net.zomis.games.dsl.impl.GameControllerScope
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.impl.SetAction
import net.zomis.games.impl.SetGame
import net.zomis.games.impl.SetGameModel
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.ais.ServerAIs
import net.zomis.games.server2.ais.gamescorers.SplendorScorers
import net.zomis.games.server2.ais.serialize
import net.zomis.games.server2.clients.WSClient
import net.zomis.games.server2.clients.getInt
import net.zomis.games.server2.clients.getText
import net.zomis.games.server2.games.PlayerGameMoveRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.lang.RuntimeException
import java.net.URI
import java.util.UUID
import kotlin.random.Random
import kotlin.reflect.KClass

class DslRandomPlayTest {

    private val logger = KLoggers.logger(this)

    var server: Server2? = null
    val config = testServerConfig()
    val serverAIs = ServerAIs(AIRepository(), emptySet())
    val random = Random.Default

    @BeforeEach
    fun startServer() {
        server = Server2(EventSystem())
        server!!.start(config)

        val tokens = mutableListOf("guest-12345", "guest-23456")
        fun authTest(message : ClientJsonMessage) {
            AuthorizationSystem(server!!.events).handleGuest(message.client, tokens.removeAt(0), UUID.randomUUID()) {""}
        }
        server!!.messageRouter.handler("auth/guest", ::authTest)
    }

    @AfterEach
    fun stopServer() {
        server!!.stop()
    }

    companion object {
        @JvmStatic
        fun serverGames(): List<Arguments> {
            return ServerGames.games.keys.sorted().map { Arguments.of(it) }
        }
    }

    fun randomSetMove(context: GameControllerScope<SetGameModel>): Actionable<SetGameModel, Any>? {
        return if (random.nextBoolean()) {
            context.model.findSets(context.model.board.cards).firstOrNull()?.let {
                context.game.actions[SetGame.callSet.name]!!.createAction(context.playerIndex, SetAction(it.map { c -> c.toStateString() }))
            }!!
        } else {
            serverAIs.randomActionable(context.game, context.playerIndex)
        }
    }

    val playingMap = mapOf<KClass<*>, GameController<*>>(
        SetGameModel::class to { context: GameControllerScope<*> -> randomSetMove(context as GameControllerScope<SetGameModel>) },
        SplendorGame::class to { ctx -> SplendorScorers.aiBuyFirst.createController().invoke(ctx as GameControllerScope<SplendorGame>) }
    )

    @ParameterizedTest(name = "Random play {0}")
    @MethodSource("serverGames")
    fun dsl(dslGame: String) {
        val p1 = WSClient(URI("ws://127.0.0.1:${config.webSocketPort}/websocket"))
        p1.connectBlocking()

        val p2 = WSClient(URI("ws://127.0.0.1:${config.webSocketPort}/websocket"))
        p2.connectBlocking()

        p1.send("""{ "route": "auth/guest" }""")
        val playerId1 = p1.expectJsonObject { it.getText("type") == "Auth" }.get("playerId").asText()
        p2.send("""{ "route": "auth/guest" }""")
        val playerId2 = p2.expectJsonObject { it.getText("type") == "Auth" }.get("playerId").asText()

        p1.send("""{ "route": "lobby/join", "gameTypes": ["$dslGame"], "maxGames": 1 }""")
        Thread.sleep(100)
        p2.send("""{ "route": "lobby/join", "gameTypes": ["$dslGame"], "maxGames": 1 }""")

        p1.send("""{ "game": "$dslGame", "type": "matchMake" }""")
        Thread.sleep(100)
        p2.send("""{ "game": "$dslGame", "type": "matchMake" }""")
        p1.expectJsonObject { it.getText("type") == "LobbyChange" }
        p1.expectJsonObject {
            it.getText("type") == "GameStarted" && it.getText("gameType") == dslGame &&
                    it.getInt("yourIndex") == 0
        }
        p2.expectJsonObject {
            it.getText("type") == "GameStarted" && it.getText("gameType") == dslGame &&
                    it.getInt("yourIndex") == 1
        }

        p1.sendAndExpectResponse("""{ "route": "games/$dslGame/1/view" }""")
        p1.expectJsonObject { it.getText("type") == "GameView" }
        p2.sendAndExpectResponse("""{ "route": "games/$dslGame/1/view" }""")
        p2.expectJsonObject { it.getText("type") == "GameView" }
        val players = arrayOf(p1, p2)

        // Find game
        val game = server!!.gameSystem.getGameType(dslGame)!!.runningGames["1"]!!
        val gameImpl = game.obj as GameImpl<*>
        val playerRange = 0 until game.players.size
        var actionCounter = 0

        while (!gameImpl.isGameOver()) {
            if (actionCounter > 10000) {
                throw RuntimeException("Game seems to be stuck. Not finishing after $actionCounter moves. Last view is ${gameImpl.view(0)}")
            }
            actionCounter++
            if (actionCounter % 10 == 0) {
                p1.sendAndExpectResponse("""{ "route": "games/$dslGame/1/view" }""")
                p1.expectJsonObject { it.getText("type") == "GameView" }
            }
            val actions: List<PlayerGameMoveRequest> = playerRange.mapNotNull {playerIndex ->
                val moveHandler = playingMap[gameImpl.model::class]
                if (moveHandler != null) {
                    val controllerContext = GameControllerContext(gameImpl, playerIndex)
                    moveHandler.invoke(controllerContext)?.let {
                        val serialized = gameImpl.actions.type(it.actionType)!!.actionType.serialize(it.parameter)
                        PlayerGameMoveRequest(game, playerIndex, it.actionType, serialized, true)
                    }
                } else {
                    serverAIs.randomAction(game, playerIndex).firstOrNull()
                }
            }.map { it.serialize(gameImpl) }
            if (actions.isEmpty()) {
                p1.sendAndExpectResponse("""{ "route": "games/$dslGame/1/view" }""")
                val view = p1.expectJsonObject { it.getText("type") == "GameView" }
                throw IllegalStateException("Game is not over but no actions available. Is the game a draw? View is $view")
            }
            val request = actions.first()
            val playerSocket = players[request.player]
            val moveString = jacksonObjectMapper().writeValueAsString(request.move)
            playerSocket.send("""{ "route": "games/$dslGame/1/move", "playerIndex": ${request.player}, "moveType": "${request.moveType}", "move": $moveString }""")
            // TODO: Also add checks for "ActionLog" messages?
            p1.takeUntilJson { it.getTextOrDefault("type", "") == "GameMove" }
            p2.takeUntilJson { it.getTextOrDefault("type", "") == "GameMove" }
        }

        // Game is finished
        var obj = p1.takeUntilJson { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.get("winner").isBoolean)
//        assert(obj.getInt("player") == 0)
//        assert(obj.getInt("position") == 1)

        obj = p1.expectJsonObject { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.get("winner").isBoolean)

        obj = p2.takeUntilJson { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.get("winner").isBoolean)

        obj = p2.expectJsonObject { it.getText("type") == "PlayerEliminated" }
        assert(obj.getText("gameType") == dslGame)
        assert(obj.get("winner").isBoolean)

        p1.expectJsonObject { it.getText("type") == "GameEnded" }
        p2.expectJsonObject { it.getText("type") == "GameEnded" }

        p1.sendAndExpectResponse("""{ "route": "games/$dslGame/1/view" }""")
        p1.expectJsonObject { it.getText("type") == "GameView" }
        p2.sendAndExpectResponse("""{ "route": "games/$dslGame/1/view" }""")
        obj = p2.expectJsonObject { it.getText("type") == "GameView" }
        val winner = obj["view"]["winner"]
        println("Winner is $winner")
        if (winner != null) {
            assert(winner.isInt) { "Winner is not an int" }
        } else {
            val eliminations = gameImpl.eliminationCallback.eliminations()
            assert(eliminations.size == 2)
        }

        p1.close()
        p2.close()
    }

}

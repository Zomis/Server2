package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*
import kotlin.random.Random

data class ActionNextParameter<T: Any, P: Any>(
    val actionType: ActionType<T, P>,
    val chosen: List<Any>,
    val parameter: P
)
data class ActionNextChoice<T: Any, P: Any>(
    val actionType: ActionType<T, *>,
    val previouslyChosen: List<Any>,
    val choiceKey: Any,
    val choiceValue: Any,
    val nextBlock: ActionChoicesScope<T, P>.(Any) -> Unit
)

class ActionComplexImpl<T: Any, P: Any>(
    val actionType: ActionType<T, P>,
    val context: ActionOptionsContext<T>,
    private val block: ActionChoicesScope<T, P>.() -> Unit
) {
/*
Some possible use-cases:
- Given choices XYZ, what are the immediate next steps? (choices + parameters)
- Given choices XYZ, and using an ActionSampleSize, give me a sequence of possible actions
*/

    fun start(): ActionComplexNextImpl<T, P> {
        val scope = ActionComplexBlockRun(actionType, emptyList(), emptyList(), context)
        block.invoke(scope)
        return scope.createNext()
    }

    fun withChosen(chosen: List<Any>): ActionComplexNextImpl<T, P> {
        val scope = ActionComplexBlockRun(actionType, emptyList(), chosen, context)
        block.invoke(scope)
        return scope.createNext()
    }

}

class ActionComplexBlockRun<T: Any, P: Any>(
    private val actionType: ActionType<T, P>,
    private val chosen: List<Any>,
    private val upcomingChoices: List<Any>,
    override val context: ActionOptionsContext<T>
): ActionChoicesScope<T, P> {
    private var blockRun: ActionComplexBlockRun<T, P> = this
    private val choices = mutableListOf<ActionNextChoice<T, P>>()
    private val parameters = mutableListOf<ActionNextParameter<T, P>>()

    override fun parameter(parameter: P) {
        if (upcomingChoices.isNotEmpty()) {
            return
        }
        parameters.add(ActionNextParameter(actionType, chosen, parameter))
    }

    private fun <E: Any> internalOptions(options: ActionOptionsScope<T>.() -> List<Pair<Any, E>>, next: ActionChoicesScope<T, P>.(E) -> Unit) {
        val evaluated = options(context)
        if (upcomingChoices.isNotEmpty()) {
            val nextChosenKey = upcomingChoices.first()
            val nextChosenList = upcomingChoices.subList(1, upcomingChoices.size)
            val nextE = evaluated.singleOrNull { it.first == nextChosenKey || it.second == nextChosenKey }
                ?: throw NoSuchElementException("Evaluated contains $evaluated and we're looking for it.first == $nextChosenKey (${nextChosenKey::class})")

            val nextScope = ActionComplexBlockRun(actionType, chosen + nextE.second, nextChosenList, context)
            next.invoke(nextScope, nextE.second)
            blockRun = nextScope
        } else {
            choices.addAll(evaluated.map { ActionNextChoice(actionType, chosen, it.first, it.second, next as ActionChoicesScope<T, P>.(Any) -> Unit) })
        }
    }

    override fun <E : Any> options(options: ActionOptionsScope<T>.() -> Iterable<E>, next: ActionChoicesScope<T, P>.(E) -> Unit) {
        return this.internalOptions({ options(context).map { it to it } }, next)
    }

    override fun <E : Any> optionsWithIds(options: ActionOptionsScope<T>.() -> Iterable<Pair<String, E>>, next: ActionChoicesScope<T, P>.(E) -> Unit) {
        return this.internalOptions({ options(context).map { it.first to it.second } }, next)
    }

    fun createNext(): ActionComplexNextImpl<T, P> {
        if (blockRun != this) return blockRun.createNext()
        return ActionComplexNextImpl(actionType, context, chosen, choices.asSequence(), parameters.asSequence())
    }

}

class ActionComplexNextImpl<T: Any, P: Any>(
    override val actionType: ActionType<T, P>,
    private val context: ActionOptionsContext<T>,
    override val chosen: List<Any>,
    private val nextChoices: Sequence<ActionNextChoice<T, P>>,
    private val nextParameters: Sequence<ActionNextParameter<T, P>>
) : ActionComplexChosenStep<T, P> {

    override val playerIndex: Int = context.playerIndex

    override fun nextOptions(): Sequence<ActionNextChoice<T, P>> = nextChoices
    override fun parameters(): Sequence<ActionNextParameter<T, P>> = nextParameters

    private fun <E> List<E>.randomSample(count: Int?, random: Random): List<E> {
        if (count == null) return this
        val indices = this.indices.toMutableList()
        val result = mutableListOf<E>()
        repeat(count) {
            result.add(this[indices.removeAt(random.nextInt(indices.size))])
        }
        return result
    }

    override fun depthFirstActions(sampling: ActionSampleSize?): Sequence<ActionNextParameter<T, P>> {
        return sequence {
            yieldAll(nextParameters)
            val (nextSampleSize, nextActionSampleSize) = sampling?.nextSample() ?: null to null
            val samples: List<ActionNextChoice<T, P>> = nextChoices.toList().randomSample(nextSampleSize, Random.Default)

            /*
            * Evaluate options
            * - Pick X of them (sample size)
            *   - Evaluate those options
            *     - Pick X of them (sample size)
            *
            * Store all parameters in one place
            * Store intermediate options in another
            */

            samples.forEach {
                val nextScope = ActionComplexBlockRun(actionType, chosen + it.choiceValue, emptyList(), context)
                it.nextBlock.invoke(nextScope, it.choiceValue)
                yieldAll(nextScope.createNext().depthFirstActions(nextActionSampleSize))
            }
        }
    }

    override fun actionKeys(): List<ActionInfoKey> {
        val parameters = parameters().map { ActionInfoKey(actionType.serialize(it.parameter), actionType.name, emptyList(), true) }
        val choices = nextOptions().map { ActionInfoKey(it.choiceKey, actionType.name, emptyList(), false) }
        return parameters.toList() + choices.toList()
    }
}
class ActionComplexChosenStepEmpty<T: Any, P: Any>(
    override val actionType: ActionType<T, P>,
    override val playerIndex: Int,
    override val chosen: List<Any>
): ActionComplexChosenStep<T, P> {

    override fun nextOptions(): Sequence<ActionNextChoice<T, P>> = emptySequence()
    override fun parameters(): Sequence<ActionNextParameter<T, P>> = emptySequence()
    override fun depthFirstActions(sampling: ActionSampleSize?): Sequence<ActionNextParameter<T, P>> = emptySequence()
    override fun actionKeys(): List<ActionInfoKey> = emptyList()

}

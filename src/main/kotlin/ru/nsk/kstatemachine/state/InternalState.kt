/*
 * Author: Mikhail Fedotov
 * Github: https://github.com/KStateMachine
 * Copyright (c) 2024.
 * All rights reserved.
 */

package ru.nsk.kstatemachine.state

import ru.nsk.kstatemachine.PathNode
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.transition.*
import ru.nsk.kstatemachine.transition.TransitionDirectionProducerPolicy.DefaultPolicy

/**
 * Contains tree composition api for [InternalState].
 * Necessary for tree algorithms testing purpose, to be able not create full states in tests.
 * This is safe to cast any [InternalNode] to [InternalState] by design.
 */
internal interface InternalNode {
    val internalParent: InternalNode?
}

internal fun InternalNode.requireParentNode(): InternalNode =
    requireNotNull(internalParent) { "$this parent is not set" }

/**
 * Defines state API for internal library usage. All states must implement this class.
 * Unfortunately cannot use interface for this purpose.
 * This is safe to cast any [IState] to [InternalState] by design for internal library implementation.
 */
abstract class InternalState : IState, InternalNode {
    override val parent: IState? get() = internalParent
    abstract override val internalParent: InternalState?
    internal abstract fun setParent(parent: InternalState)

    internal abstract fun getCurrentStates(): List<InternalState>

    internal abstract suspend fun doEnter(transitionParams: TransitionParams<*>)
    internal abstract suspend fun doExit(transitionParams: TransitionParams<*>)
    internal abstract suspend fun afterChildFinished(
        finishedChild: InternalState,
        transitionParams: TransitionParams<*>
    )

    internal open fun onParentCurrentStateChanged(currentState: InternalState) = Unit

    internal abstract suspend fun <E : Event> recursiveFindUniqueResolvedTransition(
        eventAndArgument: EventAndArgument<E>
    ): ResolvedTransition<E>?

    internal abstract suspend fun recursiveEnterInitialStates(transitionParams: TransitionParams<*>)

    /** Enters single branch path */
    internal abstract suspend fun recursiveEnterStatePath(
        path: ListIterator<InternalState>,
        transitionParams: TransitionParams<*>
    )

    /** Enters path with multiple branches */
    internal abstract suspend fun recursiveEnterStatePath(
        pathHead: PathNode,
        transitionParams: TransitionParams<*>
    )

    internal abstract suspend fun recursiveExit(transitionParams: TransitionParams<*>)
    internal abstract suspend fun recursiveStop()

    /**
     * Whether this state, or any state in its subtree, needs the [recursiveAfterTransitionComplete] callback.
     *
     * That callback is invoked for every state of the tree after each transition, which costs one virtual
     * suspend call per child state (measured: ~176 B / 45 ns per child) and dominates transitions of wide
     * state machines. Subtrees that declare no interest are not descended into.
     *
     * The default is `true`, so any direct subclass keeps today's behaviour. [BaseStateImpl] overrides this
     * with a value derived from the class hierarchy, so its subclasses do not have to declare anything.
     */
    internal open val needsAfterTransitionComplete: Boolean get() = true

    /**
     * Called after each (including initial) transition completion.
     */
    internal abstract suspend fun recursiveAfterTransitionComplete(transitionParams: TransitionParams<*>)
    internal abstract suspend fun cleanup()
}

internal fun InternalState.requireInternalParent(): InternalState =
    requireNotNull(internalParent) { "$this parent is not set" }

internal suspend fun <E : Event> InternalState.findTransitionsByEvent(event: E): List<InternalTransition<E>> {
    @Suppress("UNCHECKED_CAST")
    return transitions.filter { it.isMatchingEvent(event) } as List<InternalTransition<E>>
}

internal suspend fun <E : Event> InternalState.findUniqueResolvedTransition(
    eventAndArgument: EventAndArgument<E>
): ResolvedTransition<E>? {
    // A state without transitions cannot resolve anything, and this runs for every state on the
    // active path of every event, so skip it without allocating the intermediate lists below.
    if (transitions.isEmpty()) return null

    val policy = DefaultPolicy(eventAndArgument)
    val doNotThrowOnMultiple = machine.creationArguments.doNotThrowOnMultipleTransitionsMatch

    // Single pass instead of findTransitionsByEvent().map { }.filter { }, which allocated three
    // intermediate lists plus one Pair per matching transition on every processed event
    // (measured: -488 B on an ignored event, -336 B on a performed transition).
    var resolved: ResolvedTransition<E>? = null
    var matches = 0
    for (transition in transitions) {
        if (!transition.isMatchingEvent(eventAndArgument.event)) continue

        @Suppress("UNCHECKED_CAST")
        val candidate = transition as InternalTransition<E>
        val direction = candidate.produceTargetStateDirection(policy)
        if (direction is NoTransition) continue

        if (++matches > 1 && !doNotThrowOnMultiple) {
            error(
                "Multiple transitions match ${eventAndArgument.event}, " +
                        "${listOfNotNull(resolved, candidate to direction)} in $this"
            )
        }
        if (resolved == null) resolved = candidate to direction
        if (doNotThrowOnMultiple) return resolved
    }
    return resolved
}
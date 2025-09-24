package cn.solarmoon.spark_core.util

import kotlin.reflect.KClass

fun interface InlineEventConsumer<T: InlineEvent> {
    fun invoke(event: T)
}

interface InlineEventHandler<E: InlineEvent> {
    val eventHandlers: MutableMap<KClass<out E>, MutableList<InlineEventConsumer<out E>>>
}

class Subscription(private val unsubscribe: () -> Unit) {
    fun dispose() = unsubscribe()
}


inline fun <reified E: InlineEvent> InlineEventHandler<in E>.onEvent(handler: InlineEventConsumer<E>): Subscription {
    eventHandlers.getOrPut(E::class) { mutableListOf() }.add(handler)
    return Subscription { eventHandlers[E::class]?.remove(handler) }
}

inline fun <reified E : InlineEvent> InlineEventHandler<in E>.triggerEvent(event: E): E {
    eventHandlers[E::class]?.forEach { (it as InlineEventConsumer<E>).invoke(event) }
    return event
}

inline fun <reified E: InlineEvent> InlineEventHandler<in E>.remove(handler: InlineEventConsumer<E>) {
    eventHandlers[E::class]?.remove(handler)
}

interface InlineEvent

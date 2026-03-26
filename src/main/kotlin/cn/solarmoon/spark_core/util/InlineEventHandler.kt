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
    var anyCancelled = false
    eventHandlers[E::class]?.forEach { handler ->
        // 调用处理器，如果返回 false 则标记取消
        (handler as InlineEventConsumer<E>).invoke(event)
        if (event is CancellableEvent && event.canceled) anyCancelled = true
    }
    // 如果事件有 cancel 方法，则应用结果
    if (event is CancellableEvent) {
        event.canceled = anyCancelled
    }
    return event
}

inline fun <reified E: InlineEvent> InlineEventHandler<in E>.remove(handler: InlineEventConsumer<E>) {
    eventHandlers[E::class]?.remove(handler)
}

interface InlineEvent

interface CancellableEvent: InlineEvent {
    var canceled: Boolean
}

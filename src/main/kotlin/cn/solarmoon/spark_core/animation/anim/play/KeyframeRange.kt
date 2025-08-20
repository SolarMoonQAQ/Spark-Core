package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.js.SparkJS
import kotlin.reflect.KClass

class KeyframeRange(
    val id: String,
    val start: Double,
    val end: Double
) {

    internal lateinit var jsEngine: SparkJS

    var hasEntered = false
        private set
    var hasExited = false
        private set
    var eventHandlers = mutableMapOf<KClass<out KeyframeEvent>, MutableList<KeyframeRange.(KeyframeEvent) -> Unit>>()
        private set
    private var currentTime = 0.0

    val progress get() = ((currentTime - start) / (end - start)).coerceIn(0.0, 1.0)

    inline fun <reified T : KeyframeEvent> onEvent(crossinline handler: KeyframeRange.(T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        eventHandlers.getOrPut(T::class) { mutableListOf() }.add { handler.invoke(this, it as T) }
    }

    inline fun <reified T: KeyframeEvent> triggerEvent(event: T): T {
        eventHandlers[event::class]?.forEach { it(event) }
        return event
    }

    fun onEnter(handler: KeyframeRange.(KeyframeEvent.Enter) -> Unit) {
        onEvent<KeyframeEvent.Enter>(handler)
    }

    fun onInside(handler: KeyframeRange.(KeyframeEvent.Inside) -> Unit) {
        onEvent<KeyframeEvent.Inside>(handler)
    }

    fun onExit(handler: KeyframeRange.(KeyframeEvent.Exit) -> Unit) {
        onEvent<KeyframeEvent.Exit>(handler)
    }

    fun check(anim: AnimInstance) {
        val time = anim.typedTime
        currentTime = time
        val loop = anim.origin.loop
        val isInRange = time in start..end

        // 进入范围
        if (isInRange && !hasEntered) {
            hasEntered = true
            triggerEvent(KeyframeEvent.Enter)
        }

        // 在范围内
        if (isInRange) {
            triggerEvent(KeyframeEvent.Inside(time))
        }

        // 退出范围
        if (!isInRange && !hasExited && hasEntered) {
            hasExited = true
            triggerEvent(KeyframeEvent.Exit)
        }

        // 重置状态以便重新触发（对于循环动画）
        if (loop == Loop.TRUE) {
            if (time < start && hasExited) {
                reset()
            }
        }
    }

    fun reset() {
        hasEntered = false
        hasExited = false
        currentTime = 0.0
    }

    fun copy(): KeyframeRange {
        val new = KeyframeRange(id, start, end)
        new.hasEntered = hasEntered
        new.hasExited = hasExited
        new.eventHandlers = eventHandlers.toMutableMap()
        return new
    }

}
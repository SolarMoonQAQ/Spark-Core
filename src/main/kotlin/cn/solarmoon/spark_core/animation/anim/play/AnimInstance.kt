package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.js.extension.JSAnimation
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import kotlin.reflect.KClass

class AnimInstance private constructor(
    val holder: IAnimatable<*>,
    val index: AnimIndex
) {

    companion object {
        @JvmStatic
        fun create(holder: IAnimatable<*>, name: String, provider: AnimInstance.() -> Unit = {}) =
            create(holder, AnimIndex(holder.modelIndex.animPath, name), provider)

        @JvmStatic
        fun create(holder: IAnimatable<*>, index: AnimIndex, provider: AnimInstance.() -> Unit = {}) =
            AnimInstance(holder, index).apply { provider.invoke(this) }
    }

    val origin = OAnimationSet.get(index.index).getValidAnimation(index.name)
    val flags = setOf<String>()
    var time = 0.0
    var speed = 1.0
    var totalTime = 0.0
    var maxLength = origin.animationLength
    var shouldTurnBody = false
    var shouldTurnHead = false
    var rejectNewAnim: (AnimInstance?) -> Boolean = { false }
    var isCancelled = true
        internal set
    var eventHandlers = mutableMapOf<KClass<out AnimEvent>, MutableList<AnimInstance.(AnimEvent) -> Unit>>()
        private set

    val step get() = speed / PhysicsLevel.TPS

    fun getProgress(physPartialTicks: Float = 0f) = ((time + physPartialTicks * step) / maxLength).coerceIn(0.0, 1.0)

    fun step(overallSpeed: Double = 1.0) {
        time += step * overallSpeed
        totalTime += step * overallSpeed
    }

    inline fun <reified T : AnimEvent> onEvent(crossinline handler: AnimInstance.(T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        eventHandlers.getOrPut(T::class) { mutableListOf() }.add { handler.invoke(this, it as T) }
    }

    fun triggerEvent(event: AnimEvent) {
        eventHandlers[event::class]?.forEach { it(event) }

        if (event is AnimEvent.Completed || event is AnimEvent.SwitchOut || event is AnimEvent.Interrupted) {
            eventHandlers[AnimEvent.End::class]?.forEach { it(AnimEvent.End(event)) }
        }
    }

    fun cancel() {
        if (!isCancelled) {
            isCancelled = true
            triggerEvent(AnimEvent.Interrupted)
        }
    }

    fun copy(): AnimInstance {
        val copy = AnimInstance(holder, index)
        copy.time = time
        copy.speed = speed
        copy.totalTime = totalTime
        copy.shouldTurnBody = shouldTurnBody
        copy.shouldTurnHead = shouldTurnHead
        copy.rejectNewAnim = rejectNewAnim
        copy.eventHandlers = eventHandlers.toMutableMap()
        return copy
    }

    fun refresh() {
        time = 0.0
        totalTime = 0.0
    }

    fun tick() {
        triggerEvent(AnimEvent.Tick)
    }

    fun physTick(overallSpeed: Double = 1.0) {
        when(origin.loop) {
            Loop.TRUE -> {
                step()
            }
            Loop.ONCE -> {
                if (time < maxLength) step(overallSpeed)
                else {
                    isCancelled = true
                    triggerEvent(AnimEvent.Completed)
                }
            }
            Loop.HOLD_ON_LAST_FRAME -> {
                if (time < maxLength) step(overallSpeed)
            }
        }
    }

}
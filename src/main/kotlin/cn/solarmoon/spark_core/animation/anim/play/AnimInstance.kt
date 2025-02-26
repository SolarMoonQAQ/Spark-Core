package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import kotlin.reflect.KClass

class AnimInstance private constructor(
    val holder: IAnimatable<*>,
    val name: String,
    val origin: OAnimation
) {

    private var defaultValue: AnimInstance? = null

    companion object {
        @JvmStatic
        fun create(holder: IAnimatable<*>, name: String, origin: OAnimation = holder.animations.getAnimation(name) ?: throw NullPointerException("没有找到名为 $name 的动画"), provider: (AnimInstance).() -> Unit = {}): AnimInstance {
            val default = AnimInstance(holder, name, origin).apply { provider.invoke(this) }
            return default.apply { defaultValue = default.copy() }
        }

        @JvmStatic
        fun create(holder: IAnimatable<*>, index: AnimIndex, provider: (AnimInstance).() -> Unit = {}) = create(holder, index.name, OAnimationSet.get(index.index).getAnimation(index.name) ?: throw NullPointerException("没有找到索引为 $index 的动画"), provider)
    }

    var time = 0.0
    var speed = 1.0
    var totalTime = 0.0
    var maxLength = origin.animationLength
    var shouldTurnBody = false
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
        triggerEvent(AnimEvent.PhysicsTick)
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

    fun getDefault() = defaultValue!!

    fun copy(): AnimInstance {
        val copy = AnimInstance(holder, name, origin)
        copy.defaultValue = defaultValue
        copy.time = time
        copy.speed = speed
        copy.totalTime = totalTime
        copy.shouldTurnBody = shouldTurnBody
        copy.rejectNewAnim = rejectNewAnim
        copy.eventHandlers = eventHandlers.toMutableMap()
        return copy
    }

    fun refresh() {
        defaultValue?.let { default ->
            time = default.time
            speed = default.speed
            totalTime = default.totalTime
            shouldTurnBody = default.shouldTurnBody
            rejectNewAnim = default.rejectNewAnim
            eventHandlers = default.eventHandlers.toMutableMap()
        }
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
package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import org.joml.Vector2d
import kotlin.collections.getOrPut
import kotlin.reflect.KClass

class AnimInstance internal constructor(
    val holder: IAnimatable<*>,
    val animIndex: AnimIndex
) {

    val origin = OAnimationSet.getOrEmpty(animIndex.modelIndex.location).getValidAnimation(animIndex.name)
    val flags = setOf<String>()
    var time = 0.0
    var speed = 1.0
    var weight = 1.0
    var currentWeight = 0.0
        private set
    var inTransitionTime = 0.15f
    var outTransitionTime = 0.15f
    var transitionTick = 0
        private set
    var transitionState = TransitionState.NONE
        private set
    val isInTransition get() = transitionState != TransitionState.NONE
    var maxLength = origin.animationLength
    var shouldTurnBody = false
    // 锁定ai注视目标
    var shouldTurnHead = true
    var rejectNewAnim: (AnimInstance?) -> Boolean = { false }
    var isCancelled = true
        internal set(value) {
            if (!field && value) triggerEvent(AnimEvent.End)
            field = value
        }
    var eventHandlers = mutableMapOf<KClass<out AnimEvent>, MutableList<AnimInstance.(AnimEvent) -> Unit>>()
        private set
    var keyframeRanges = mutableMapOf<String, KeyframeRange>()
        private set
    var paused = false
    var selfDriving = false
    var group = AnimGroups.MAIN

    val inTransitionTick get() = (inTransitionTime * PhysicsLevel.TPS).toInt()
    val outTransitionTick get() = (outTransitionTime * PhysicsLevel.TPS).toInt()

    val step get() = speed / PhysicsLevel.TPS

    val typedTime get() = when (origin.loop) {
        Loop.TRUE -> time % origin.animationLength
        Loop.ONCE -> time
        Loop.HOLD_ON_LAST_FRAME -> time
    }

    fun enter() {
        transitionState = TransitionState.ENTER
        transitionTick = inTransitionTick
        currentWeight = 0.0
        isCancelled = false
        holder.animController.layers.getOrPut(group) { AnimLayer() }.animations.add(this)
    }

    fun exit() {
        transitionState = TransitionState.EXIT
        transitionTick = outTransitionTick
        if (!isCancelled) {
            isCancelled = true
            triggerEvent(AnimEvent.Interrupted)
        }
    }

    fun getProgress(physPartialTicks: Float = 0f) = ((time + physPartialTicks * step) / (if (paused) time + step else maxLength)).coerceIn(0.0, 1.0)

    fun step(overallSpeed: Double = 1.0) {
        if (paused || selfDriving) return
        if (isInTransition) updateWeight()
        else time += step * overallSpeed
    }

    private fun updateWeight() {
        when(transitionState) {
            TransitionState.ENTER -> {
                val progress = 1.0 - (transitionTick.toDouble() / inTransitionTick)
                currentWeight = weight * progress
                if (--transitionTick <= 0) {
                    currentWeight = weight
                    transitionState = TransitionState.NONE
                }
            }
            TransitionState.EXIT -> {
                val progress = transitionTick.toDouble() / outTransitionTick
                currentWeight = weight * progress
                if (--transitionTick <= 0) {
                    currentWeight = 0.0
                    transitionState = TransitionState.NONE
                }
            }
            TransitionState.NONE -> {
                currentWeight = weight
            }
        }
    }

    inline fun <reified T : AnimEvent> onEvent(crossinline handler: AnimInstance.(T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        eventHandlers.getOrPut(T::class) { mutableListOf() }.add { handler.invoke(this, it as T) }
    }

    inline fun <reified T: AnimEvent> triggerEvent(event: T): T {
        eventHandlers[event::class]?.forEach { it(event) }
        return event
    }

    fun refresh() {
        time = 0.0
        keyframeRanges.values.forEach { it.reset() }
    }

    fun tick() {
        triggerEvent(AnimEvent.Tick)
        keyframeRanges.forEach { (id, range) -> range.check(this) }
    }

    fun physTick(overallSpeed: Double = 1.0) {
        when(origin.loop) {
            Loop.TRUE -> {
                step()
            }
            Loop.ONCE -> {
                if (time < maxLength) step(overallSpeed)
                else if (!isCancelled) {
                    holder.animLevel?.submitImmediateTask {
                        isCancelled = true
                        triggerEvent(AnimEvent.Completed)
                    }
                }
            }
            Loop.HOLD_ON_LAST_FRAME -> {
                if (time < maxLength) step(overallSpeed)
            }
        }
    }

    // 关键帧系统方法
    /**
     * 注册一个关键帧范围
     * @param id 范围的唯一标识符
     * @param start 开始时间
     * @param end 结束时间
     * @return KeyframeRange对象，可用于注册事件处理器
     */
    fun registerKeyframeRange(id: String, start: Double, end: Double): KeyframeRange {
        val range = KeyframeRange(id, start, end)
        keyframeRanges[id] = range
        return range
    }

    fun registerKeyframeRangeEnd(id: String, end: Double) = registerKeyframeRange(id, 0.0, end)

    fun registerKeyframeRangeStart(id: String, start: Double) = registerKeyframeRange(id, start, maxLength)

    fun registerKeyframeRanges(id: String, vararg range: Vector2d, provider: KeyframeRange.(Int) -> Unit = {}): List<KeyframeRange> {
        val kfs = mutableListOf<KeyframeRange>()
        range.forEachIndexed { index, r ->
            val kf = registerKeyframeRange("$id$index", r.x, r.y)
            provider(kf, index)
            kfs.add(kf)
        }
        return kfs.toList()
    }

    /**
     * 移除一个关键帧范围
     * @param id 范围的唯一标识符
     */
    fun removeKeyframeRange(id: String) {
        keyframeRanges.remove(id)
    }

}
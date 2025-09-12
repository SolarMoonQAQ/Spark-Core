package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.util.PPhase
import org.joml.Vector2d
import kotlin.reflect.KClass

class AnimInstance private constructor(
    val holder: IAnimatable<*>,
    val animIndex: AnimIndex
) {

    companion object {
        @JvmStatic
        fun create(holder: IAnimatable<*>, name: String, provider: AnimInstance.() -> Unit = {}): AnimInstance? {
            return create(holder, AnimIndex(holder.modelController.model?.index!!, name), provider)
        }

        @JvmStatic
        fun create(holder: IAnimatable<*>, index: AnimIndex, provider: AnimInstance.() -> Unit = {}) =
            try {
                AnimInstance(holder, index).apply { provider.invoke(this) }
            } catch (e: Exception) {
                SparkCore.logger("动画").error(e.message)
                null
            }
    }

    val origin = OAnimationSet.getOrEmpty(animIndex.modelIndex.location).getValidAnimation(animIndex.name)
    val flags = setOf<String>()
    var time = 0.0
    var speed = 1.0
    var totalTime = 0.0
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

    val step get() = speed / PhysicsLevel.TPS

    val typedTime get() = when (origin.loop) {
        Loop.TRUE -> time % origin.animationLength
        Loop.ONCE -> time
        Loop.HOLD_ON_LAST_FRAME -> time
    }

    fun getProgress(physPartialTicks: Float = 0f) = ((time + physPartialTicks * step) / maxLength).coerceIn(0.0, 1.0)

    fun step(overallSpeed: Double = 1.0) {
        time += step * overallSpeed
        totalTime += step * overallSpeed
    }

    inline fun <reified T : AnimEvent> onEvent(crossinline handler: AnimInstance.(T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        eventHandlers.getOrPut(T::class) { mutableListOf() }.add { handler.invoke(this, it as T) }
    }

    inline fun <reified T: AnimEvent> triggerEvent(event: T): T {
        eventHandlers[event::class]?.forEach { it(event) }
        return event
    }

    fun cancel() {
        if (!isCancelled) {
            isCancelled = true
            triggerEvent(AnimEvent.Interrupted)
        }
    }

    fun copy(): AnimInstance {
        val copy = AnimInstance(holder, animIndex)
        copy.time = time
        copy.speed = speed
        copy.totalTime = totalTime
        copy.shouldTurnBody = shouldTurnBody
        copy.shouldTurnHead = shouldTurnHead
        copy.rejectNewAnim = rejectNewAnim
        copy.eventHandlers = eventHandlers.toMutableMap()
        copy.keyframeRanges = keyframeRanges.mapValues { it.value.copy() }.toMutableMap()
        return copy
    }

    fun refresh() {
        time = 0.0
        totalTime = 0.0
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
                    holder.animLevel.submitImmediateTask {
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
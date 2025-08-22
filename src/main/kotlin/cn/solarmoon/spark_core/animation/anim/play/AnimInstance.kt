package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import cn.solarmoon.spark_core.util.PPhase
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2d
import kotlin.reflect.KClass

class AnimInstance private constructor(
    val holder: IAnimatable<*>,
    val animIndex: AnimIndex
) {

    companion object {
        // 见鬼了,排查了两个小时也没找到为啥animPath会变成modelPath
        @JvmStatic
        fun create(holder: IAnimatable<*>, name: String, provider: AnimInstance.() -> Unit = {}): AnimInstance {
            var animPath = holder.modelIndex.animPath
            SparkCore.LOGGER.warn("AnimInstance.create - 获取到的animPath: $animPath")
            SparkCore.LOGGER.warn("AnimInstance.create - modelPath: ${holder.modelIndex.modelPath}")

            // 临时修复：如果animPath包含models，自动转换为animations
            if (animPath.path.contains("models")) {
                SparkCore.LOGGER.error("发现BUG: animPath包含models而不是animations! 自动修复中...")
                animPath = SparkResourcePathBuilder.buildAnimationPathFromModel(animPath)
                SparkCore.LOGGER.warn("修复后的animPath: $animPath")

                // 同时修复ModelIndex中的animPath，避免下次还有问题
                holder.modelIndex.animPath = animPath
                SparkCore.LOGGER.warn("已更新ModelIndex.animPath为: $animPath")
            }

            return create(holder, AnimIndex(animPath, name, useShortcutConversion = false), provider)
        }

        @JvmStatic
        fun create(holder: IAnimatable<*>, index: AnimIndex, provider: AnimInstance.() -> Unit = {}) =
            AnimInstance(holder, index).apply { provider.invoke(this) }
    }
    private val nameComponents = animIndex.toString().split("/")
    // OAnimationSet的index为 animIndex-animName
    val animName = nameComponents.last()
    val setIndex = ResourceLocation.parse(nameComponents.dropLast(1).joinToString("/"))
    val origin = OAnimationSet.get(setIndex).getValidAnimation(animName)
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
                    holder.animLevel.submitImmediateTask(PPhase.POST) {
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
        val range = KeyframeRange(id, start, end).apply { jsEngine = holder.animLevel.jsEngine }
        keyframeRanges[id] = range
        return range
    }

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
package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.resource.common.SparkResourcePathBuilder
import net.minecraft.resources.ResourceLocation
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

            return create(holder, AnimIndex(animPath, name), provider)
        }

        @JvmStatic
        fun create(holder: IAnimatable<*>, index: AnimIndex, provider: AnimInstance.() -> Unit = {}) =
            AnimInstance(holder, index).apply { provider.invoke(this) }
    }
    private val nameComponents = animIndex.toString().split("/")
    // animIndex的真正的index为 animIndex-animName
    val index = ResourceLocation.parse(nameComponents.dropLast(1).joinToString("/"))
    val origin = OAnimationSet.get(index).getValidAnimation(nameComponents.last())
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
        val copy = AnimInstance(holder, animIndex)
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
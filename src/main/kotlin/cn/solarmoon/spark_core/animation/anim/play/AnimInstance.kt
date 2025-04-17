package cn.solarmoon.spark_core.animation.anim.play

// Assume CalikoStructureBuilder exists in this path based on the diff context
import au.edu.federation.caliko.FabrikChain3D
import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.ik.caliko.CalikoStructureBuilder
import cn.solarmoon.spark_core.js.extension.JSAnimation
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import kotlin.reflect.KClass

class AnimInstance private constructor(
    val holder: IAnimatable<*>,
    val name: String,
    val origin: OAnimation
) {

    companion object {
        @JvmStatic
        fun create(holder: IAnimatable<*>, name: String, origin: OAnimation = holder.animations.getValidAnimation(name), provider: AnimInstance.() -> Unit = {}): AnimInstance {
            return AnimInstance(holder, name, origin).apply { provider.invoke(this) }
        }

        @JvmStatic
        fun create(holder: IAnimatable<*>, index: AnimIndex, provider: AnimInstance.() -> Unit = {}) = create(holder, index.name, OAnimationSet.get(index.index).getValidAnimation(index.name), provider)
    }

    val flags = setOf<String>()

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

    // 用于存储构建好的 IK 链，键是链的名称 (例如 "left_arm_ik")
    private val ikChains = mutableMapOf<String, FabrikChain3D>()

    val step get() = speed / PhysicsLevel.TPS

    fun getProgress(physPartialTicks: Float = 0f) = ((time + physPartialTicks * step) / maxLength).coerceIn(0.0, 1.0)

    fun step(overallSpeed: Double = 1.0) {
        time += step * overallSpeed
        totalTime += step * overallSpeed
    }

    /**
     * 设置并构建一个 IK 链。
     * 应在 AnimInstance 创建后，且 holder.model 可用时调用。
     * 通常在 AnimInstance.create 的 provider lambda 中调用。
     *
     * @param chainName IK 链的唯一名称 (例如 "arm_ik", "leg_ik")。
     * @param startBoneName IK 链的起始骨骼名称。
     * @param endBoneName IK 链的末端效应器骨骼名称。
     * @return 如果成功构建并存储了 IK 链，则返回 true；否则返回 false。
     */
//    fun setupIkChain(chainName: String, startBoneName: String, endBoneName: String): Boolean {
//        val model = holder.model
//        // Assuming CalikoStructureBuilder.buildChain exists and works as intended
//        val chain = CalikoStructureBuilder.buildChain(holder, chainName, startBoneName, endBoneName, model)
//        return if (chain != null) {
//            ikChains[chainName] = chainworldPoseMatrix
//            SparkCore.LOGGER.debug("Successfully built IK chain '$chainName' for ${holder.animatable}")
//            true
//        } else {
//            SparkCore.LOGGER.warn("Failed to build IK chain '$chainName' (start: $startBoneName, end: $endBoneName) for ${holder.animatable}")
//            false
//        }
//    }

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

    /**
     * 获取指定名称的已构建 IK 链。
     *
     * @param chainName IK 链的名称。
     * @return FabrikChain3D 实例，如果未找到则返回 null。
     */
    fun getIkChain(chainName: String): FabrikChain3D? = ikChains[chainName]

    fun copy(): AnimInstance {
        val copy = AnimInstance(holder, name, origin)
        copy.time = time
        copy.speed = speed
        copy.totalTime = totalTime
        copy.shouldTurnBody = shouldTurnBody
        copy.rejectNewAnim = rejectNewAnim
        copy.eventHandlers = eventHandlers.toMutableMap() // Shallow copy of handlers map
        copy.ikChains.putAll(this.ikChains) // Creates a shallow copy of the map itself, chains are still shared references

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
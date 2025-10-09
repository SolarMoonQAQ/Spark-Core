package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import net.minecraft.nbt.CompoundTag
import org.joml.Vector2d
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.activeStates
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.transition.onTriggered
import kotlin.collections.getOrPut
import kotlin.reflect.KClass

class AnimInstance internal constructor(
    val holder: IAnimatable<*>,
    val animIndex: AnimIndex
) {

    sealed class AnimStateEvent {
        object Start: Event
        object TransitionFinish: Event
        object Stop: Event // 打断
        object Finish: Event // 完成
    }

    val state get() = AnimState.valueOf(lifecycleStateMachine.activeStates().first().name!!.uppercase())

    val origin = OAnimationSet.getOrEmpty(animIndex.modelIndex.location).getValidAnimation(animIndex.name)
    val tag = CompoundTag()
    var time = 0.0
    var speed = 1.0
    var weight = 1.0
    var currentWeight = 0.0
        private set
    var inTransitionTime = 0.15f
    var outTransitionTime = 0.15f
    var transitionTick = 0
        private set
    val isInTransition get() = state in listOf(AnimState.ENTER, AnimState.EXIT)
    var maxLength = origin.animationLength
    var shouldTurnBody = false
    // 锁定ai注视目标
    var shouldTurnHead = true
    var rejectNewAnim: (AnimInstance?) -> Boolean = { false }
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

    private val lifecycleStateMachine = createStdLibStateMachine {
        val idle = initialState("idle")
        val enter = state("enter")
        val work = state("work")
        val exit = state("exit")

        idle.apply {
            onEntry {
                refresh()
            }

            transition<AnimStateEvent.Start> {
                targetState = enter
            }
        }

        enter.apply {
            onEntry {
                transitionTick = inTransitionTick
                currentWeight = 0.0
                holder.animController.layers.getOrPut(group) { AnimLayer() }.animations.add(this@AnimInstance)
            }

            transition<AnimStateEvent.Stop> {
                targetState = exit
                onTriggered {
                    triggerEvent(AnimEvent.Interrupted)
                }
            }

            transition<AnimStateEvent.TransitionFinish> {
                targetState = work
            }
        }

        work.apply {
            transition<AnimStateEvent.Stop> {
                targetState = exit
                onTriggered {
                    triggerEvent(AnimEvent.Interrupted)
                }
            }

            transition<AnimStateEvent.Finish> {
                targetState = exit
                onTriggered {
                    triggerEvent(AnimEvent.Completed)
                }
            }
        }

        exit.apply {
            onEntry {
                transitionTick = outTransitionTick
                triggerEvent(AnimEvent.End)
            }

            transition<AnimStateEvent.TransitionFinish> {
                targetState = idle
            }
        }
    }

    fun enter() {
        lifecycleStateMachine.processEventBlocking(AnimStateEvent.Start)
    }

    fun exit() {
        lifecycleStateMachine.processEventBlocking(AnimStateEvent.Stop)
    }

    fun getProgress(physPartialTicks: Float = 0f) = ((time + physPartialTicks * step) / (if (paused) time + step else maxLength)).coerceIn(0.0, 1.0)

    fun step(overallSpeed: Double = 1.0) {
        if (paused || selfDriving) return
        else time += step * overallSpeed
    }

    fun stepWeight() {
        when(state) {
            AnimState.ENTER -> {
                val progress = 1.0 - (transitionTick.toDouble() / inTransitionTick)
                currentWeight = weight * progress
                if (--transitionTick <= 0) {
                    currentWeight = weight
                    lifecycleStateMachine.processEventBlocking(AnimStateEvent.TransitionFinish)
                }
            }
            AnimState.EXIT -> {
                val progress = transitionTick.toDouble() / outTransitionTick
                currentWeight = weight * progress
                if (--transitionTick <= 0) {
                    currentWeight = 0.0
                    lifecycleStateMachine.processEventBlocking(AnimStateEvent.TransitionFinish)
                }
            }
            else -> {}
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
        if (isInTransition) stepWeight()
        else {
            when(origin.loop) {
                Loop.TRUE -> {
                    step(overallSpeed)
                }
                Loop.ONCE -> {
                    if (time < maxLength) step(overallSpeed)
                    else lifecycleStateMachine.processEventBlocking(AnimStateEvent.Finish)
                }
                Loop.HOLD_ON_LAST_FRAME -> {
                    if (time < maxLength) step(overallSpeed)
                }
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
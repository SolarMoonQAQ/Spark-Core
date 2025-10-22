package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.js.eval
import cn.solarmoon.spark_core.js.molang.JSMolangValue
import cn.solarmoon.spark_core.js.safeGetOrCreateJSContext
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import kotlinx.serialization.json.Json
import net.minecraft.nbt.CompoundTag
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.serialization.persistence.KStateMachineSerializersModule
import ru.nsk.kstatemachine.state.activeStates
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.transition.onTriggered
import kotlin.collections.getOrPut
import kotlin.reflect.KClass

class AnimInstance internal constructor(
    val holder: IAnimatable<*>,
    val animIndex: AnimIndex
) {

    private sealed class AnimStateEvent {
        object Start: Event
        object TransitionFinish: Event
        object Stop: Event // 打断
        object Finish: Event // 完成
    }

    val state get() = AnimState.valueOf(lifecycleStateMachine.activeStates().firstOrNull()?.name?.uppercase() ?: "IDLE")

    val origin = OAnimationSet.getOrEmpty(animIndex.modelIndex).getAnimation(animIndex.name) ?: throw IllegalArgumentException("没有找到索引为 $animIndex 的动画")
    val tag = CompoundTag()
    var time = 0.0f
    var speed = 1.0f
    var weight = 1.0f
    var currentWeight = 0.0f
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
    var paused = false
    var selfDriving = false
    var group = AnimGroups.MAIN
    private val notifies = mutableListOf<AnimNotify>()

    val inTransitionTick get() = (inTransitionTime * PhysicsLevel.TPS).toInt()
    val outTransitionTick get() = (outTransitionTime * PhysicsLevel.TPS).toInt()

    val step get() = speed / PhysicsLevel.TPS

    val typedTime get() = when (origin.loop) {
        Loop.TRUE -> time % origin.animationLength
        Loop.ONCE -> time
        Loop.HOLD_ON_LAST_FRAME -> time
    }

    init {
        origin.timeline.forEach { timeline, script0 ->
            registerNotify(AnimNotify.Point("fromOrigin", timeline)).onEnter {
                val script = JSMolangValue(script0.joinToString(""))
                triggerEvent(AnimEvent.Notify(this, script))
                script.eval(this@AnimInstance)
            }
        }
    }

    private val lifecycleStateMachine by lazy {
        createStdLibStateMachine {
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
                    currentWeight = 0.0f
                    holder.animController.playAnimation(this@AnimInstance)
                    triggerEvent(AnimEvent.Start)
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
                    if (it.event is AnimStateEvent.Finish) holder.animLevel?.submitImmediateTask {
                        triggerEvent(AnimEvent.End)
                    } else {
                        triggerEvent(AnimEvent.End)
                    }
                }

                transition<AnimStateEvent.TransitionFinish> {
                    targetState = idle
                }
            }
        }
    }

    fun independentEnter() {
        holder.animController.stopAnimation(group)
        enter()
    }

    fun enter() {
        holder.animLevel!!.physicsLevel.submitImmediateTask {
            lifecycleStateMachine.processEventBlocking(AnimStateEvent.Start)
        }
    }

    fun exit() {
        holder.animLevel!!.physicsLevel.submitImmediateTask {
            lifecycleStateMachine.processEventBlocking(AnimStateEvent.Stop)
        }
    }

    fun getProgress(physPartialTicks: Float = 0f) = ((time + physPartialTicks * step) / (if (paused) time + step else maxLength)).coerceIn(0.0f, 1.0f)

    fun step(overallSpeed: Float = 1.0f) {
        if (paused || selfDriving) return
        else time += step * overallSpeed
    }

    fun stepWeight() {
        when(state) {
            AnimState.ENTER -> {
                val progress = if (inTransitionTick <= 0) 1f else 1.0f - (transitionTick.toFloat() / inTransitionTick)
                currentWeight = weight * progress
                if (--transitionTick <= 0) {
                    currentWeight = weight
                    lifecycleStateMachine.processEventBlocking(AnimStateEvent.TransitionFinish)
                }
            }
            AnimState.EXIT -> {
                val progress = if (outTransitionTick <= 0) 0f else transitionTick.toFloat() / outTransitionTick
                currentWeight = weight * progress
                if (--transitionTick <= 0) {
                    currentWeight = 0.0f
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
        time = 0.0f
    }

    fun tick() {
        triggerEvent(AnimEvent.Tick)
        notifies.forEach { it.check(this) }
    }

    fun physTick(overallSpeed: Float = 1.0f) {
        if (isInTransition) stepWeight()
        else {
            when(origin.loop) {
                Loop.TRUE -> {
                    step(overallSpeed)
                }
                Loop.ONCE -> {
                    if (time <= maxLength) step(overallSpeed)
                    else {
                        lifecycleStateMachine.processEventBlocking(AnimStateEvent.Finish)
                    }
                }
                Loop.HOLD_ON_LAST_FRAME -> {
                    if (time < maxLength) step(overallSpeed)
                }
            }
        }
    }

    // 关键帧系统方法

    fun <N: AnimNotify> registerNotify(notify: N): N {
        notifies += notify
        return notify
    }

}
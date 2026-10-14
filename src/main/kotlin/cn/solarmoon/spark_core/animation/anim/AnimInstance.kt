package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.api.physicsLevel
import cn.solarmoon.spark_core.api.submitImmediateTask
import cn.solarmoon.spark_core.js.molang.JSMolangValue
import cn.solarmoon.spark_core.js.molang.evalAsDouble
import net.minecraft.nbt.CompoundTag
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.*
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.transition.onTriggered
import kotlin.reflect.KClass

/**
 * 动画实例 —— 封装单个动画的播放生命周期。
 *
 * 提供两个构造路径：
 * - [AnimInstance(IAnimatable, OAnimation)] — 直接传入已解析的 [OAnimation]，
 *   用于 MultiAnimStateMachine 回退动画场景（动画来自素体/内置集而非 target 自身模型）。
 * - [AnimInstance(IAnimatable, AnimIndex)] — 通过 [AnimIndex] 从持有者模型集中查找，
 *   标准路径，用于动画来自 target 自身模型的场景。
 */
class AnimInstance internal constructor(
    val holder: IAnimatable<*>,
    val origin: OAnimation
) {
    /**
     * 通过 [AnimIndex] 从持有者模型集中查找动画。
     * 标准查找路径，用于动画来自 target 自身模型的场景。
     */
    constructor(holder: IAnimatable<*>, index: AnimIndex) : this(
        holder,
        OAnimationSet.getOrEmpty(index.modelIndex).getAnimation(index.name)
            ?: throw IllegalArgumentException("没有找到索引为 $index 的动画")
    )

    private sealed class AnimStateEvent {
        object Start : Event
        object TransitionFinish : Event
        object Stop : Event // 打断
        object Finish : Event // 完成
    }

    val state get() = AnimState.valueOf(lifecycleStateMachine.activeStates().firstOrNull()?.name?.uppercase() ?: "IDLE")
    val tag = CompoundTag()
    var time = 0.0f
    var speed = 1.0f
    var weight = 1.0f
    var currentWeight = 0.0f
        internal set
    var inTransitionTime = 0.15f
    var outTransitionTime = 0.15f
    var transitionTick = 0
        private set
    val isInTransition get() = state in listOf(AnimState.ENTER, AnimState.EXIT)
    val isFinished get() = state == AnimState.IDLE
    var maxLength = origin.animationLength
    var shouldTurnBody = false

    // 锁定ai注视目标
    var shouldTurnHead = true
    var rejectNewAnim: (AnimInstance?) -> Boolean = { false }
    var eventHandlers = mutableMapOf<KClass<out AnimEvent>, MutableList<AnimInstance.(AnimEvent) -> Unit>>()
        private set
    var paused = false
    var selfDriving = false
    var group = AnimGroups.POSTURE
    private val notifies = mutableListOf<AnimNotify>()

    val inTransitionTick get() = (inTransitionTime * tps).toInt()
    val outTransitionTick get() = (outTransitionTime * tps).toInt()

    val step get() = speed / tps

    val typedTime
        get() = when (origin.loop) {
            Loop.TRUE -> time % origin.animationLength
            Loop.ONCE -> time
            Loop.HOLD_ON_LAST_FRAME -> time
        }

    val tps
        get() = if (holder.animLevel != null) {
            holder.animLevel!!.physicsLevel.tps
        } else 60

    init {
        origin.timeline.forEach { timeline, script0 ->
            registerNotify(AnimNotify.Point("fromOrigin", timeline)).onEnter {
                val script = JSMolangValue(script0.joinToString(""))
                triggerEvent(AnimEvent.Notify(this, script))
                script.evalAsDouble(this@AnimInstance)
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

    /**
     * 进入播放。持有者的 [IAnimatable.animLevel] 已关联 PhysicsLevel 时投递到物理线程，
     * 否则退化为在调用线程直接处理事件（没有物理线程会驱动这个实例，故不引入并发；
     * 同时消掉原先的裸 `!!`）。
     */
    fun enter() {
        val event = AnimStateEvent.Start
        holder.animLevel?.physicsLevel?.submitImmediateTask {
            lifecycleStateMachine.processEventBlocking(event)
        } ?: lifecycleStateMachine.processEventBlocking(event)
    }

    /**
     * 请求退出。语义与 [enter] 对称：[IAnimatable.animLevel] 就绪时投递到物理线程，
     * 否则在调用线程直接处理。
     * 已处于 IDLE 的实例收到 Stop 会被生命周期状态机忽略，重复调用安全。
     */
    fun exit() {
        val event = AnimStateEvent.Stop
        holder.animLevel?.physicsLevel?.submitImmediateTask {
            lifecycleStateMachine.processEventBlocking(event)
        } ?: lifecycleStateMachine.processEventBlocking(event)
    }

    fun getProgress(physPartialTicks: Float = 0f) =
        ((time + physPartialTicks * step) / (if (paused) time + step else maxLength)).coerceIn(0.0f, 1.0f)

    fun step(overallSpeed: Float = 1.0f) {
        if (paused || selfDriving) return
        else time += step * overallSpeed
    }

    fun stepWeight() {
        when (state) {
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

    inline fun <reified T : AnimEvent> triggerEvent(event: T): T {
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
            when (origin.loop) {
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

    fun <N : AnimNotify> registerNotify(notify: N): N {
        notifies += notify
        return notify
    }

}
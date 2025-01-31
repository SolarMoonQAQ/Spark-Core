package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation
import cn.solarmoon.spark_core.physics.level.PhysicsLevel

class AnimInstance private constructor(
    val holder: IAnimatable<*>,
    val name: String,
    val origin: OAnimation
) {

    private var defaultValue: AnimInstance? = null

    companion object {
        @JvmStatic
        fun create(holder: IAnimatable<*>, name: String, origin: OAnimation = holder.animations.getAnimation(name) ?: throw NullPointerException("找不到名为 $name 的动画"), provider: (AnimInstance).() -> Unit = {}): AnimInstance {
            val default = AnimInstance(holder, name, origin).apply { provider.invoke(this) }
            return default.apply { defaultValue = default.copy() }
        }
    }

    var time = 0.0
    var speed = 1.0
    var totalTime = 0.0
    var maxLength = origin.animationLength
    var shouldTurnBody = false
    var rejectNewAnim: (AnimInstance?) -> Boolean = { false }
    var isCancelled = true
        private set

    private var physTickActions = mutableListOf<AnimInstance.() -> Unit>()
    private var tickActions = mutableListOf<AnimInstance.() -> Unit>()
    private var switchActions = mutableListOf<AnimInstance.(AnimInstance?) -> Unit>()
    private var enableActions = mutableListOf<AnimInstance.() -> Unit>()
    private var endActions = mutableListOf<AnimInstance.() -> Unit>()

    val step get() = speed / PhysicsLevel.TPS

    fun getProgress(physPartialTicks: Float = 0f) = ((time + physPartialTicks * step) / maxLength).coerceIn(0.0, 1.0)

    fun step(overallSpeed: Double = 1.0) {
        time += step * overallSpeed
        totalTime += step * overallSpeed
    }

    fun onTick(reset: Boolean = false, action: AnimInstance.() -> Unit) {
        if (reset) tickActions.clear()
        tickActions.add(action)
    }

    fun onPhysTick(reset: Boolean = false, action: AnimInstance.() -> Unit) {
        if (reset) physTickActions.clear()
        physTickActions.add(action)
    }

    fun onSwitch(reset: Boolean = false, action: AnimInstance.(AnimInstance?) -> Unit) {
        if (reset) switchActions.clear()
        switchActions.add(action)
    }

    fun onEnable(reset: Boolean = false, action: AnimInstance.() -> Unit) {
        if (reset) enableActions.clear()
        enableActions.add(action)
    }

    fun onEnd(reset: Boolean = false, action: AnimInstance.() -> Unit) {
        if (reset) endActions.clear()
        endActions.add(action)
    }

    fun switchInvoke(next: AnimInstance?) {
        switchActions.forEach {
            it.invoke(this, next)
        }
    }

    fun cancel() {
        isCancelled = true
        endActions.forEach {
            it.invoke(this)
        }
    }

    fun enable() {
        isCancelled = false
        enableActions.forEach {
            it.invoke(this)
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
        copy.physTickActions = physTickActions.toMutableList()
        copy.tickActions = tickActions.toMutableList()
        copy.switchActions = switchActions.toMutableList()
        copy.enableActions = enableActions.toMutableList()
        copy.endActions = endActions.toMutableList()
        return copy
    }

    fun refresh() {
        defaultValue?.let { default ->
            time = default.time
            speed = default.speed
            totalTime = default.totalTime
            shouldTurnBody = default.shouldTurnBody
            rejectNewAnim = default.rejectNewAnim

            physTickActions = default.physTickActions.toMutableList()
            tickActions = default.tickActions.toMutableList()
            switchActions = default.switchActions.toMutableList()
            enableActions = default.enableActions
            endActions = default.endActions.toMutableList()
        }
    }

    fun tick() {
        tickActions.forEach { it.invoke(this) }
    }
    
    fun physTick(overallSpeed: Double = 1.0) {
        when(origin.loop) {
            Loop.TRUE -> {
                step()
            }
            Loop.ONCE -> {
                if (time < maxLength) step(overallSpeed)
                else {
                    cancel()
                }
            }
            Loop.HOLD_ON_LAST_FRAME -> {
                if (time < maxLength) step(overallSpeed)
            }
        }

        physTickActions.forEach {
            it.invoke(this)
        }
    }

}
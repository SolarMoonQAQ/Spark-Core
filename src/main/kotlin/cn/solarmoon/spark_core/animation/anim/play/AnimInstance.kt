package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.Loop
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation

class AnimInstance(
    val holder: IAnimatable<*>,
    val name: String,
    val origin: OAnimation
) {
    constructor(holder: IAnimatable<*>, name: String): this(holder, name, holder.animations.getAnimation(name)!!)

    var time = 0.0
    var speed = 1.0
    var totalTime = 0.0
    var shouldTurnBody = false
    var rejectNewAnim: (AnimInstance?) -> Boolean = { false }
    var isCancelled = false
    private var tickAction: AnimInstance.() -> Unit = {}
    private var switchAction: AnimInstance.(AnimInstance?) -> Unit = {}

    val step get() = speed / 20

    fun getProgress(partialTicks: Float = 0f) = ((time + partialTicks / (speed * 20)) / origin.animationLength).coerceIn(0.0, 1.0)

    fun step(overallSpeed: Double = 1.0) {
        time += step * overallSpeed
        totalTime += step * overallSpeed
    }

    fun onTick(action: AnimInstance.() -> Unit) {
        tickAction = action
    }

    fun onSwitch(action: AnimInstance.(AnimInstance?) -> Unit) {
        switchAction = action
    }

    fun switch(next: AnimInstance?) {
        switchAction.invoke(this, next)
    }
    
    fun tick(overallSpeed: Double = 1.0) {
        when(origin.loop) {
            Loop.TRUE -> {
                step()
            }
            Loop.ONCE -> {
                if (time < origin.animationLength) step(overallSpeed)
                else {
                    isCancelled = true
                }
            }
            Loop.HOLD_ON_LAST_FRAME -> {
                if (time < origin.animationLength) step(overallSpeed)
            }
        }

        tickAction.invoke(this)
    }

}
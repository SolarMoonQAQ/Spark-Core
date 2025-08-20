package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.util.PPhase
import org.mozilla.javascript.Function

interface JSAnimation {

    val anim get() = this as AnimInstance

    val js get() = anim.holder.animLevel.jsEngine

    fun getProgress() = anim.getProgress(1f)

    fun onSwitchIn(consumer: Function) {
        anim.onEvent<AnimEvent.SwitchIn> {
            val p = it.previous
            consumer.call(js, p)
        }
    }

    fun onSwitchOut(consumer: Function) {
        anim.onEvent<AnimEvent.SwitchOut> {
            val n = it.originNextAnim
            consumer.call(js, n)
        }
    }

    fun onEnd(consumer: Function) {
        anim.onEvent<AnimEvent.End> {
            consumer.call(js)
        }
    }

    fun onCompleted(consumer: Function) {
        anim.onEvent<AnimEvent.Completed> {
            consumer.call(js)
        }
    }

    fun onStart(consumer: Function) {
        anim.onEvent<AnimEvent.SwitchIn> {
            consumer.call(js, it.previous)
        }
    }

    fun setShouldTurnBody(bool: Boolean) {
        anim.shouldTurnBody = bool
    }

    fun registerKeyframeRangeEnd(id: String, end: Double) = anim.registerKeyframeRange(id, 0.0, end)

    fun registerKeyframeRangeStart(id: String, start: Double) = anim.registerKeyframeRange(id, start, anim.maxLength)

}
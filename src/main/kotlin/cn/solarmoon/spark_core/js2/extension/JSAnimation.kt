package cn.solarmoon.spark_core.js2.extension

import cn.solarmoon.spark_core.animation.anim.play.AnimEvent
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.js2.toArray
import cn.solarmoon.spark_core.js2.toVec2
import cn.solarmoon.spark_core.util.toVector2d
import org.graalvm.polyglot.Value

interface JSAnimation {

    val anim get() = this as AnimInstance

    fun getProgress() = anim.getProgress(1f)

    fun onSwitchIn(consumer: Value) {
        anim.onEvent<AnimEvent.SwitchIn> {
            val p = it.previous
            consumer.execute(p)
        }
    }

    fun onSwitchOut(consumer: Value) {
        anim.onEvent<AnimEvent.SwitchOut> {
            val n = it.originNextAnim
            consumer.execute(n)
        }
    }

    fun onEnd(consumer: Value) {
        anim.onEvent<AnimEvent.End> {
            consumer.execute()
        }
    }

    fun onCompleted(consumer: Value) {
        anim.onEvent<AnimEvent.Completed> {
            consumer.execute()
        }
    }

    fun onStart(consumer: Value) {
        anim.onEvent<AnimEvent.SwitchIn> {
            consumer.execute(it.previous)
        }
    }

    fun setShouldTurnBody(bool: Boolean) {
        anim.shouldTurnBody = bool
    }

    fun registerKeyframeRangeEnd(id: String, end: Double) = anim.registerKeyframeRange(id, 0.0, end)

    fun registerKeyframeRangeStart(id: String, start: Double) = anim.registerKeyframeRange(id, start, anim.maxLength)

    fun registerKeyframeRanges(id: String, ranges: Value, provider: Value) = anim.registerKeyframeRanges(id, *ranges.toArray { it.toVec2().toVector2d() }) {
        provider.execute(this, it)
    }

}
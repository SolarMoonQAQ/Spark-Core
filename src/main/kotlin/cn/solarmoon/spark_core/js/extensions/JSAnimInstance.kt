package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.animation.anim.AnimEvent
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.KeyframeRange
import cn.solarmoon.spark_core.js.doc.JSClass
import org.joml.Vector2d

@JSClass("AnimInstance")
interface JSAnimInstance {

    val js_self get() = this as AnimInstance

    fun getProgress() = js_self.getProgress(1f)

    fun onStart(consumer: () -> Unit) {
        js_self.onEvent<AnimEvent.Start> {
            consumer()
        }
    }

    fun onEnd(consumer: () -> Unit) {
        js_self.onEvent<AnimEvent.End> {
            consumer()
        }
    }

    fun onCompleted(consumer: () -> Unit) {
        js_self.onEvent<AnimEvent.Completed> {
            consumer()
        }
    }

    fun js_registerKeyframeRangeEnd(id: String, end: Double): KeyframeRange = js_self.registerKeyframeRangeEnd(id, end)

    fun js_registerKeyframeRangeStart(id: String, start: Double): KeyframeRange = js_self.registerKeyframeRangeStart(id, start)

    fun registerKeyframeRanges(id: String, range: List<DoubleArray>, provider: (KeyframeRange, Int) -> Unit): List<KeyframeRange> =
        js_self.registerKeyframeRanges(id, *range.map { Vector2d(it[0], it[1]) }.toTypedArray()) { index ->
            provider(this, index)
        }

}
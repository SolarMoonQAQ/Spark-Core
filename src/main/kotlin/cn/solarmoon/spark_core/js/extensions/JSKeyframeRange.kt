package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.animation.anim.play.KeyframeEvent
import cn.solarmoon.spark_core.animation.anim.play.KeyframeRange
import cn.solarmoon.spark_core.js.doc.JSClass

@JSClass("KeyframeRange")
interface JSKeyframeRange {

    val self get() = this as KeyframeRange

    fun onEnter(handler: KeyframeRange.(KeyframeEvent.Enter) -> Unit) {
        self.onEnter(handler)
    }

    fun onInside(handler: KeyframeRange.(KeyframeEvent.Inside) -> Unit) {
        self.onInside(handler)
    }

    fun onExit(handler: KeyframeRange.(KeyframeEvent.Exit) -> Unit) {
        self.onExit(handler)
    }

}
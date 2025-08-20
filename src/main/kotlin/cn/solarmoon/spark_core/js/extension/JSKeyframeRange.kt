package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.anim.play.KeyframeRange
import cn.solarmoon.spark_core.js.call
import org.mozilla.javascript.Function

interface JSKeyframeRange {

    val self get() = this as KeyframeRange

    val js get() = self.jsEngine

    fun onEnter(consumer: Function) {
        self.onEnter {
            consumer.call(js)
        }
    }

    fun onInside(consumer: Function) {
        self.onInside {
            consumer.call(js, it.time)
        }
    }

    fun onExit(consumer: Function) {
        self.onExit {
            consumer.call(js)
        }
    }

}
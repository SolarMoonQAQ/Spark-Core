package cn.solarmoon.spark_core.js2.extension

import cn.solarmoon.spark_core.animation.anim.play.KeyframeRange
import org.graalvm.polyglot.Value

interface JSKeyframeRange {

    val self get() = this as KeyframeRange

    fun onEnter(consumer: Value) {
        self.onEnter {
            consumer.execute()
        }
    }

    fun onInside(consumer: Value) {
        self.onInside {
            consumer.execute(it.time)
        }
    }

    fun onExit(consumer: Value) {
        self.onExit {
            consumer.execute()
        }
    }

}
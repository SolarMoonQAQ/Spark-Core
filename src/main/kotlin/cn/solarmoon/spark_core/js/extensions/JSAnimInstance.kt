package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.animation.anim.AnimEvent
import cn.solarmoon.spark_core.animation.anim.AnimInstance
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

}
package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.js.doc.JSClass

@JSClass("Animatable")
interface JSAnimatable {

    val self get() = this as IAnimatable<*>

    fun changeSpeed(time: Int, speed: Double) {
        if (time > 0) {
            self.animController.changeSpeed(time, speed)
        }
    }

}
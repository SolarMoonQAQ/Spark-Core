package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex

fun animInstance(holder: IAnimatable<*>, name: String, logger: Boolean = true, provider: AnimInstance.() -> Unit = {}): AnimInstance? {
    return animInstance(holder, AnimIndex(holder.modelController.model?.index!!, name), logger, provider)
}

fun animInstance(holder: IAnimatable<*>, index: AnimIndex, logger: Boolean = true, provider: AnimInstance.() -> Unit = {}) =
    try {
        AnimInstance(holder, index).apply { provider.invoke(this) }
    } catch (e: Exception) {
        if (logger) SparkCore.logger("动画").error(e.message)
        null
    }
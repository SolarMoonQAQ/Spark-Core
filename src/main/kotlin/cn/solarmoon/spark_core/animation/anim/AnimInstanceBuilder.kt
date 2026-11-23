package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex

/**
 * 按名实例化动画体当前绑定的动画集里的片段。
 *
 * 片段从 [cn.solarmoon.spark_core.animation.anim.AnimController.originAnimations] 解析后直接构造实例，
 * 不再按模型索引查一次：动画集由动画体自己的资源键决定，与模型的 LOD 距离无关。
 */
fun animInstance(holder: IAnimatable<*>, name: String, logger: Boolean = true, provider: AnimInstance.() -> Unit = {}): AnimInstance? {
    val animation = holder.animController.originAnimations.getAnimation(name)
    if (animation == null) {
        if (logger) SparkCore.logger("动画").error("没有找到名为 $name 的动画")
        return null
    }
    return try {
        AnimInstance(holder, animation).apply { provider.invoke(this) }
    } catch (e: Exception) {
        if (logger) SparkCore.logger("动画").error(e.message)
        null
    }
}

/**
 * 按显式索引实例化：索引里的模型与片段名照传入值查找，不受动画体当前绑定的动画集影响。
 */
fun animInstance(holder: IAnimatable<*>, index: AnimIndex, logger: Boolean = true, provider: AnimInstance.() -> Unit = {}) =
    try {
        AnimInstance(holder, index).apply { provider.invoke(this) }
    } catch (e: Exception) {
        if (logger) SparkCore.logger("动画").error(e.message)
        null
    }

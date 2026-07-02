package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation

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

/**
 * 用预解析的 [OAnimation] 构建 AnimInstance（跳过内部动画查找）。
 *
 * 用于 MultiAnimStateMachine 的回退动画场景 ——
 * 动画可能来自素体/内置动画集而非 target 自身模型，
 * 直接传入已解析的 animation 避免在 target 的 [OAnimationSet] 中查找失败。
 *
 * @param holder  动画体（SubPart 或主机体）
 * @param animName 动画名（用于构建 [AnimIndex]）
 * @param animation 已通过 [cn.solarmoon.spark_core.animation.state.MultiAnimStateMachine.findAnimation] 解析的动画
 */
fun buildInstance(
    holder: IAnimatable<*>,
    animName: String,
    animation: OAnimation
): AnimInstance {
    val modelIndex = holder.modelController.model?.index ?: throw IllegalStateException(
        "无法获取 ${holder::class.simpleName} 的模型索引"
    )
    return AnimInstance(holder, AnimIndex(modelIndex, animName), originOverride = animation)
}
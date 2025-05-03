package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import net.neoforged.bus.api.Event

/**
 * 当IAnimatable的ModelIndex发生变化时触发的事件
 * 这个事件允许其他系统（如IK系统）响应模型变化
 */
class ModelIndexChangeEvent(
    val animatable: IAnimatable<*>,
    val oldModelIndex: ModelIndex,
    val newModelIndex: ModelIndex
) : Event() {
    /**
     * 检查ModelIndex的ikPath是否发生了变化
     */
    val isIkPathChanged: Boolean
        get() = oldModelIndex.ikPath != newModelIndex.ikPath
}

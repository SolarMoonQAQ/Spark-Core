package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.animation.model.ModelInstance
import net.neoforged.bus.api.Event

/**
 * 当IAnimatable的ModelIndex发生变化时触发的事件
 * 这个事件允许其他系统（如IK系统）响应模型变化
 */
class ModelChangeEvent(
    val animatable: IAnimatable<*>,
    val oldModel: ModelInstance?,
    val originNewModel: ModelInstance?
) : Event() {

    var newModel: ModelInstance? = originNewModel

}

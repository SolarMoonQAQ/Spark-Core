package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.js.doc.JSGlobal

@JSGlobal("AnimInstance")
object JSAnimInstanceGlobal {

    fun create(animatable: IAnimatable<*>, name: String): AnimInstance? = AnimInstance.create(animatable, name)

}
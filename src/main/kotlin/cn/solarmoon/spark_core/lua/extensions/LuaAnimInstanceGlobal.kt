package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.lua.doc.LuaGlobal

@LuaGlobal("AnimInstance")
object LuaAnimInstanceGlobal {

    fun create(animatable: IAnimatable<*>, name: String) = AnimInstance.create(animatable, name)

}
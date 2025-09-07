package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.animation.anim.play.layer.getMainLayer
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.lua.doc.LuaClass
import net.minecraft.resources.ResourceLocation

@LuaClass("Animatable")
interface LuaAnimatable {

    val lua_animatable get() = this as IAnimatable<*>

    fun getAnimation(): AnimInstance? {
        return lua_animatable.animController.getMainLayer().animation
    }

    fun playAnimation(anim: AnimInstance, transitionTime: Int) {
        lua_animatable.animController.getMainLayer().setAnimation(anim, AnimLayerData(enterTransitionTime = transitionTime))
    }

    fun createAnimation(index: String, name: String): AnimInstance {
        return AnimInstance.create(lua_animatable, AnimIndex(ModelIndex(ResourceLocation.parse(index)), name))!!
    }

    fun createAnimation(name: String): AnimInstance {
        return AnimInstance.create(lua_animatable, name)!!
    }

    fun changeSpeed(time: Int, speed: Double) {
        if (time > 0) {
            lua_animatable.animController.changeSpeed(time, speed)
        }
    }

}
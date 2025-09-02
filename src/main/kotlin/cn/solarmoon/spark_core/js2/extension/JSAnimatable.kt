package cn.solarmoon.spark_core.js2.extension

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.animation.anim.play.layer.getMainLayer
import net.minecraft.resources.ResourceLocation

interface JSAnimatable {

    val js_animatable get() = this as IAnimatable<*>

    val js get() = js_animatable.animLevel.jsEngine

    fun getAnimation(): AnimInstance? {
        return js_animatable.animController.getMainLayer().animation
    }

    fun playAnimation(anim: AnimInstance, transitionTime: Int) {
        js_animatable.animController.getMainLayer().setAnimation(anim, AnimLayerData(enterTransitionTime = transitionTime))
    }

    fun createAnimation(index: String, name: String): AnimInstance {
        return AnimInstance.create(js_animatable, AnimIndex(ResourceLocation.parse(index), name))!!
    }

    fun createAnimation(name: String): AnimInstance {
       return AnimInstance.create(js_animatable, name)!!
    }

    fun changeSpeed(time: Int, speed: Double) {
        if (time > 0) {
            js_animatable.animController.changeSpeed(time, speed)
        }
    }

}
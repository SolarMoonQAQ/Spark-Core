package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.animation.anim.play.layer.getMainLayer
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.js.doc.JSClass
import net.minecraft.resources.ResourceLocation

@JSClass("Animatable")
interface JSAnimatable {

    val self get() = this as IAnimatable<*>

    fun getAnimation(): AnimInstance? {
        return self.animController.getMainLayer().animation
    }

    fun playAnimation(anim: AnimInstance, transitionTime: Int) {
        self.animController.getMainLayer().setAnimation(anim, AnimLayerData(enterTransitionTime = transitionTime))
    }

    fun changeSpeed(time: Int, speed: Double) {
        if (time > 0) {
            self.animController.changeSpeed(time, speed)
        }
    }

}
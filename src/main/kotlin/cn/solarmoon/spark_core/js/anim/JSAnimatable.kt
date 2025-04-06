package cn.solarmoon.spark_core.js.anim

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.HostAccess

class JSAnimatable(
    val animatable: IAnimatable<*>
) {

    @HostAccess.Export
    fun getAnimation(): JSAnimation? {
        val anim = animatable.animController.getPlayingAnim()
        return anim?.let { JSAnimation(it) }
    }

    @HostAccess.Export
    fun playAnimation(anim: JSAnimation, transitionTime: Int) {
        animatable.animController.setAnimation(anim.anim, transitionTime)
    }

    @HostAccess.Export
    fun createAnimation(index: String, name: String): JSAnimation {
        return JSAnimation(AnimInstance.create(animatable, AnimIndex(ResourceLocation.parse(index), name)))
    }

    @HostAccess.Export
    fun createAnimation(name: String): JSAnimation {
        return JSAnimation(AnimInstance.create(animatable, name))
    }

}
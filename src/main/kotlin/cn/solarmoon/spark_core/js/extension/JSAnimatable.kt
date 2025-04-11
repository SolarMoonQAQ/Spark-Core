package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor
import org.graalvm.polyglot.HostAccess

interface JSAnimatable {

    val js_animatable get() = this as IAnimatable<*>

    val js get() = js_animatable.animLevel.jsEngine

    @HostAccess.Export
    fun getAnimation(): AnimInstance? {
        return js_animatable.animController.getPlayingAnim()
    }

    @HostAccess.Export
    fun playAnimation(anim: AnimInstance, transitionTime: Int) {
        js_animatable.animController.setAnimation(anim, transitionTime)
    }

    @HostAccess.Export
    fun createAnimation(index: String, name: String): AnimInstance {
        return AnimInstance.create(js_animatable, AnimIndex(ResourceLocation.parse(index), name))
    }

    @HostAccess.Export
    fun createAnimation(name: String): AnimInstance {
        return AnimInstance.create(js_animatable, name)
    }

    fun changeSpeed(time: Int, speed: Double) {
        if (!js_animatable.animLevel.isClientSide && time > 0) {
            js_animatable.animController.changeSpeed(time, speed)
            PacketDistributor.sendToAllPlayers(AnimSpeedChangePayload(js_animatable, time, speed))
        }
    }

}
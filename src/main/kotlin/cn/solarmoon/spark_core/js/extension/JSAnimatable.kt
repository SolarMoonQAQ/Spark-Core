package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor
import java.awt.Color

interface JSAnimatable {

    val js_animatable get() = this as IAnimatable<*>

    val js get() = js_animatable.animLevel.jsEngine

    fun getAnimation(): AnimInstance? {
        return js_animatable.animController.getPlayingAnim()
    }

    fun playAnimation(anim: AnimInstance, transitionTime: Int) {
        js_animatable.animController.setAnimation(anim, transitionTime)
    }

    fun createAnimation(index: String, name: String): AnimInstance {
        return AnimInstance.create(js_animatable, AnimIndex(ResourceLocation.parse(index), name))
    }

    fun createAnimation(index: String): AnimInstance {
        // 直接从注册表中获取
       return (SparkRegistries.TYPED_ANIMATION.get(ResourceLocation.parse(index)) as TypedAnimation).create(js_animatable)
    }

    fun changeSpeed(time: Int, speed: Double) {
        if (!js_animatable.animLevel.isClientSide && time > 0) {
            js_animatable.animController.changeSpeed(time, speed)
            PacketDistributor.sendToAllPlayers(AnimSpeedChangePayload(js_animatable, time, speed))
        }
    }

    fun summonShadow(maxLifeTime: Int, color: Int) {
        val animatable = js_animatable
        if (animatable is Entity && !animatable.animLevel.isClientSide) {
            SparkVisualEffects.SHADOW.addToClient(animatable.id, maxLifeTime, Color(color))
        }
    }

}
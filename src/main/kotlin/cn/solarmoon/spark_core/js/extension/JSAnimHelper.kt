package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.js.JSComponent
import net.minecraft.resources.ResourceLocation

object JSAnimHelper: JSComponent() {

    fun createModelIndex(model: String, texture: String) = ModelIndex(
        ResourceLocation.parse(model),
        ResourceLocation.parse(texture),
    )

}
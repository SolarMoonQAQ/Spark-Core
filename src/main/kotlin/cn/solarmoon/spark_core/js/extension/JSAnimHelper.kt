package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import net.minecraft.resources.ResourceLocation
import org.graalvm.polyglot.HostAccess

class JSAnimHelper {

    @HostAccess.Export
    fun createModelIndex(model: String, texture: String) = ModelIndex(
        ResourceLocation.parse(model),
        ResourceLocation.parse(texture),
    )

}
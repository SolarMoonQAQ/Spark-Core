package cn.solarmoon.spark_core.animation.model

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.model.origin.OModel
import net.minecraft.resources.ResourceLocation
import kotlin.collections.getOrPut

class ModelInstance(
    val animatable: IAnimatable<*>,
    val index: ModelIndex
) {

    val origin = OModel.getOrEmpty(index.location)

    val pose = ModelPose(this)

    var textureLocation: ResourceLocation = ResourceLocation.withDefaultNamespace("missing")

}
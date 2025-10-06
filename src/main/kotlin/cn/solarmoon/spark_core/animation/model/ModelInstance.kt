package cn.solarmoon.spark_core.animation.model

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.model.origin.OModel
import net.minecraft.resources.ResourceLocation

class ModelInstance(
    val animatable: IAnimatable<*>,
    val index: ModelIndex
) {

    val origin get() = OModel.getOrEmpty(index.location)

    val pose = ModelPose(this)

    var textureLocation: ResourceLocation = ResourceLocation.fromNamespaceAndPath(index.location.namespace, "textures/${index.location.path}.png")

}
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

    val bonePoses = BonePoseGroup(this)

    var textureLocation: ResourceLocation = ResourceLocation.fromNamespaceAndPath("minecraft", "empty")

    fun getBonePose(name: String) = bonePoses[name]!! // 理论上骨骼组创建时已经根据当前origin获取了所有骨骼，所以不存在不存在的骨骼

    fun getBonePoseOrCreateEmpty(name: String) = bonePoses.getOrPut(name) { BonePose(this, name) }

}
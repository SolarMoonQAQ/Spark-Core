package cn.solarmoon.spark_core.animation.model

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.event.ModelChangeEvent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.properties.Delegates

class ModelController(
    val animatable: IAnimatable<*>
) {

    private var currentModel: ModelInstance? = when(animatable) {
        is IEntityAnimatable<*> -> ModelInstance(animatable, ModelIndex.of(animatable.animatable.type))
        else -> null
    }

    val originModel get() = OModel.getOrEmpty(model?.index?.location)

    val model get() = currentModel

    val textureLocation get() = model?.textureLocation ?: ResourceLocation.withDefaultNamespace("missingno")

    fun setModel(index: ModelIndex?) {
        val m = index?.let { ModelInstance(animatable, it) }
        setModel(m)
    }

    fun setModel(model: ModelInstance?) {
        val event = NeoForge.EVENT_BUS.post(ModelChangeEvent(animatable, currentModel, model))
        currentModel = event.newModel
    }

    fun setTextureLocation(location: ResourceLocation?) {
        model?.textureLocation = location ?: ResourceLocation.withDefaultNamespace("missingno")
    }

}
package cn.solarmoon.spark_core.api.visual_effect.common.trail

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.api.phys.obb.OrientedBoundingBox
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Vector3f
import java.awt.Color
import java.io.FileNotFoundException

class Trail(
    val box: OrientedBoundingBox,
    val axis: Direction.Axis,
    val color: Color = Color.WHITE
) {

    private var textureLocation = DEFAULT_TEXTURE
    val start: Vector3f get() = box.getAxisFaceCenters(axis).first
    val end: Vector3f get() = box.getAxisFaceCenters(axis).second

    var tick = 0
    var maxTick = 5

    val isFinished get() = tick > maxTick
    fun getProgress(partialTicks: Float = 0f) = ((tick + partialTicks) / maxTick).coerceIn(0f, 1f)

    fun tick() {
        if (isFinished) return
        tick += 1
    }

    fun setTexture(itemStack: ItemStack) {
        val id = BuiltInRegistries.ITEM.getKey(itemStack.item)
        setTexture(ResourceLocation.fromNamespaceAndPath(id.namespace, "textures/item/${id.path}.png"))
    }

    fun setTexture(location: ResourceLocation) {
        try {
            Minecraft.getInstance().resourceManager.getResourceOrThrow(location)
            textureLocation = location
        } catch (e: FileNotFoundException) {
            textureLocation = DEFAULT_TEXTURE
        }
    }

    fun getTexture() = textureLocation

    companion object {
        @JvmStatic
        val DEFAULT_TEXTURE = ResourceLocation.withDefaultNamespace("textures/item/iron_ingot.png")
    }

}
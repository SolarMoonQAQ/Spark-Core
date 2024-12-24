package cn.solarmoon.spark_core.visual_effect.common.trail

import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Vector3f
import org.ode4j.ode.DGeom
import java.awt.Color
import java.io.FileNotFoundException

class Trail(
    val box: DGeom,
    val axis: Direction.Axis,
    val color: Color = Color.WHITE
) {

    private var textureLocation = DEFAULT_TEXTURE
    val start: Vector3f get() = Vector3f()
    val end: Vector3f get() = Vector3f()

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
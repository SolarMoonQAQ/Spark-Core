package cn.solarmoon.spark_core.visual_effect.common.trail

import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import java.awt.Color
import java.io.FileNotFoundException

class Trail(
    val length: Float,
    val center: Vector3f,
    val rotation: Quaternionf,
    val axis: Direction.Axis,
    val color: Color = Color.WHITE
) {

    val direction = when(axis) {
        Direction.Axis.X -> Vector3f(1f, 0f, 0f)
        Direction.Axis.Y -> Vector3f(0f, 1f, 0f)
        Direction.Axis.Z -> Vector3f(0f, 0f, 1f)
    }
    // 计算旋转向量
    private val rotatedDirection = direction.rotate(rotation)

    val start get() = center.sub(rotatedDirection.mul(length / 2, Vector3f()), Vector3f())

    val end get() = center.add(rotatedDirection.mul(length / 2, Vector3f()), Vector3f())

    private var textureLocation = DEFAULT_TEXTURE

    var tick = 0
    var maxTick = 7
    var isRemoved = false
        private set

    fun getProgress(partialTicks: Float = 0f) = ((tick + partialTicks) / maxTick).coerceIn(0f, 1f)

    fun tick() {
        if (isRemoved) return
        if (tick < maxTick) tick++
        else remove()
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

    fun remove() {
        isRemoved = true
    }

    companion object {
        @JvmStatic
        val DEFAULT_TEXTURE = ResourceLocation.withDefaultNamespace("textures/item/iron_ingot.png")
    }

}
package cn.solarmoon.spark_core.visual_effect.common.trail

import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import org.ode4j.ode.DGeom
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVector3f
import java.awt.Color
import java.io.FileNotFoundException

class Trail(
    val length: Float,
    val center: Vector3f,
    val rotation: Quaternionf,
    val color: Color = Color.WHITE
) {

    val start get() = Matrix4f().translate(center.add(0f, 0f, length / 2, Vector3f())).rotate(rotation).getTranslation(Vector3f())
    val end get() = Matrix4f().translate(center.sub(0f, 0f, length / 2, Vector3f())).rotate(rotation).getTranslation(Vector3f())

    fun lerp(target: Trail, progress: Float) = Trail(
        length,
        center.lerp(target.center, progress, Vector3f()),
        rotation.slerp(target.rotation, progress, Quaternionf()),
        color
    )

    private var textureLocation = DEFAULT_TEXTURE

    var tick = 0
    var maxTick = 5
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
package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.AnimController
import cn.solarmoon.spark_core.animation.model.ModelController
import cn.solarmoon.spark_core.animation.model.ModelIndex
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import org.joml.Matrix4f

open class ItemAnimatable(
    val itemStack: ItemStack,
    override val animLevel: Level
) : IAnimatable<ItemStack> {

    var owner: Entity? = null

    override val animatable = itemStack
    override val defaultModelIndex: ModelIndex
        get() {
            val res = BuiltInRegistries.ITEM.getKey(itemStack.item)
            return ModelIndex(ResourceLocation.fromNamespaceAndPath(res.namespace, "item/${res.path}"))
        }
    override val animController = AnimController(this)
    override val modelController = ModelController(this)

    open fun physicsTick() {
        animController.physTick()
    }

    open fun inventoryTick(owner: Entity) {
        if (this.owner != owner) this.owner = owner
    }

    override fun getWorldPositionMatrix(partialTicks: Number): Matrix4f {
        val owner = owner ?: return Matrix4f()
        return Matrix4f()
            .translate(owner.getPosition(partialTicks.toFloat()).toVector3f())
            .rotateY(owner.getViewYRot(partialTicks.toFloat()))
    }

}
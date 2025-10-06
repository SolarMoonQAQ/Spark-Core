package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.play.layer.AnimController
import cn.solarmoon.spark_core.animation.model.ModelController
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import cn.solarmoon.spark_core.util.toRadians
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import kotlin.math.PI

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
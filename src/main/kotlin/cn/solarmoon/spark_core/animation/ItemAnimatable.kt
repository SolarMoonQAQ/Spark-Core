package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.AnimController
import cn.solarmoon.spark_core.animation.model.ModelController
import cn.solarmoon.spark_core.animation.model.ModelIndex
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

open class ItemAnimatable(
    val itemStack: ItemStack,
    override val animLevel: Level
) : IAnimatable<ItemStack> {

    var owner: Entity? = null

    override val animatable = itemStack
    override val defaultModelIndex: ModelIndex get() = ModelIndex.of(itemStack.item)
    override val animController = AnimController(this)
    override val modelController = ModelController(this)
    override val variables = mutableMapOf<String, Any>()

    open fun physicsTick() {
        animController.physTick()
    }

    open fun inventoryTick(owner: Entity) {
        if (this.owner != owner) this.owner = owner
    }

    /**
     * 轻量世界坐标查询——直接取持有者位置，零矩阵分配。
     * 无持有者时返回零向量（与默认实现一致）。
     */
    override fun getRenderPosition(partialTicks: Number): Vec3 {
        return owner?.getPosition(partialTicks.toFloat()) ?: Vec3.ZERO
    }

    override fun getWorldPositionMatrix(partialTicks: Number): Matrix4f {
        val owner = owner ?: return Matrix4f()
        return Matrix4f()
            .translate(owner.getPosition(partialTicks.toFloat()).toVector3f())
            .rotateY(owner.getViewYRot(partialTicks.toFloat()))
    }

}
package cn.solarmoon.spark_core.animation

import au.edu.federation.caliko.FabrikChain3D
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimController
import cn.solarmoon.spark_core.animation.model.ModelController
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

open class ItemAnimatable(
    val itemStack: ItemStack,
    override val animLevel: Level
) : IAnimatable<ItemStack> {

    var position = Vec3.ZERO
    var oPosition = Vec3.ZERO
    var yRot = 0f
    var oYRot = 0f

    override val animatable = itemStack
    override val animController: AnimController = AnimController(this)
    override val modelController: ModelController = ModelController(this)
    override val ikTargetPositions: MutableMap<String, Vec3> = mutableMapOf()
    override val ikChains: MutableMap<String, FabrikChain3D> = mutableMapOf()

    open fun physicsTick() {
        animController.physTick()
    }

    open fun inventoryTick(owner: Entity) {
        position = owner.getPosition(1f)
        oPosition = owner.getPosition(0f)
        yRot = owner.getViewYRot(1f)
        oYRot = owner.getViewYRot(0f)
    }

    override fun getWorldPosition(partialTick: Float): Vec3 {
        return oPosition.lerp(position, partialTick.toDouble())
    }

    override fun getRootYRot(partialTick: Float): Float {
        return Mth.lerp(oYRot, yRot, partialTick)
    }

    override val syncerType: SyncerType
        get() = TODO("Not yet implemented")
    override val syncData: SyncData
        get() = TODO("Not yet implemented")

}
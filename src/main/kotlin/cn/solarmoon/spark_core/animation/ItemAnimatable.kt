package cn.solarmoon.spark_core.animation

import au.edu.federation.caliko.FabrikChain3D
import cn.solarmoon.spark_core.animation.anim.play.AnimController
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.event.ItemStackInventoryTickEvent
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
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

    override var modelIndex: ModelIndex = ModelIndex.of(EntityType.PLAYER)
        set(value) {
            field = value
            bones = BoneGroup(this)//重设模型时更新骨骼组
        }
    override val tempStorage: ITempVariableStorage = VariableStorage()
    override val scopedStorage: IScopedVariableStorage = VariableStorage()
    override val foreignStorage: IForeignVariableStorage = VariableStorage()
    override val animatable = itemStack
    override val animController: AnimController = AnimController(this)
    override val bones: BoneGroup = BoneGroup(this)
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
        animController.tick()
    }

    override fun getWorldPosition(partialTick: Float): Vec3 {
        return oPosition.lerp(position, partialTick.toDouble())
    }

    override fun getRootYRot(partialTick: Float): Float {
        return Mth.lerp(oYRot, yRot, partialTick)
    }

    override fun equals(other: Any?): Boolean {
        return (other as? ItemAnimatable)?.modelIndex == modelIndex
    }

    override fun hashCode(): Int {
        return modelIndex.hashCode()
    }

    override val syncerType: SyncerType
        get() = TODO("Not yet implemented")
    override val syncData: SyncData
        get() = TODO("Not yet implemented")

}
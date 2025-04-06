package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.event.ItemStackInventoryTickEvent
import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import cn.solarmoon.spark_core.registry.common.SparkCapabilities
import cn.solarmoon.spark_core.registry.common.SparkDataComponents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object AnimApplier {

    @SubscribeEvent
    private fun playerRespawn(event: PlayerEvent.Clone) {
        if (event.isWasDeath) {
            event.entity.setData(SparkAttachments.MODEL_INDEX, event.original.getData(SparkAttachments.MODEL_INDEX))
        }
    }

    @SubscribeEvent
    private fun physTick(event: PhysicsEntityTickEvent) {
        val entity = event.entity
        if (entity is IEntityAnimatable<*>) {
            entity.animController.physTick()
        }

        if (entity is Player) {
            entity.inventory.items.forEach {
                it.getCapability(SparkCapabilities.ITEM_ANIMATABLE, entity.level())?.physicsTick()
            }
        }
    }

    @SubscribeEvent
    private fun entityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        if (entity is IEntityAnimatable<*>) {
            entity.animController.tick()
        }
    }

    @SubscribeEvent
    private fun itemTick(event: ItemStackInventoryTickEvent) {
        val stack = event.stack
        stack.getCapability(SparkCapabilities.ITEM_ANIMATABLE, event.entity.level())?.inventoryTick(event.entity)
    }

    @SubscribeEvent
    private fun onBoneUpdate(event: BoneUpdateEvent) {
        val animatable = event.animatable
        animatable.onBoneUpdate(event)
    }

}
package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatableItem
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.event.ItemStackInventoryTickEvent
import cn.solarmoon.spark_core.event.PhysTickEvent
import cn.solarmoon.spark_core.registry.common.SparkAttachments
import cn.solarmoon.spark_core.registry.common.SparkDataComponents
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object AnimApplier {

    @SubscribeEvent
    private fun entityJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        entity.setData(SparkAttachments.MODEL_INDEX, ModelIndex.of(entity))
    }

    @SubscribeEvent
    private fun playerRespawn(event: PlayerEvent.Clone) {
        if (event.isWasDeath) {
            event.entity.setData(SparkAttachments.MODEL_INDEX, event.original.getData(SparkAttachments.MODEL_INDEX))
        }
    }

    @SubscribeEvent
    private fun physTick(event: PhysTickEvent.Entity) {
        val entity = event.entity
        if (entity is IEntityAnimatable<*>) {
            entity.animController.physTick()
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
        val item = stack.item
        if (item is IAnimatableItem) {
            stack.update(SparkDataComponents.ANIMATABLE, ItemAnimatable(ModelIndex.of(item))) { old ->
                item.onUpdate(old, event)
                old.updatePos(item.getPosition(event))
                old.animController.physTick()
                old
            }
        }
    }

    @SubscribeEvent
    private fun onBoneUpdate(event: BoneUpdateEvent) {
        val animatable = event.animatable
        animatable.onBoneUpdate(event)
    }

}
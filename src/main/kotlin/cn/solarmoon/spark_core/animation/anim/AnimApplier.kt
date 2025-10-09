package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.event.BoneUpdateEvent
import cn.solarmoon.spark_core.event.ItemStackInventoryTickEvent
import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import cn.solarmoon.spark_core.registry.common.SparkCapabilities
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object AnimApplier {

    @SubscribeEvent
    private fun playerJoin(event: EntityJoinLevelEvent) {
        val entity = event.entity
        if (entity is Player && entity.isLocalPlayer && entity is LocalPlayer) {
            entity.modelController.setTextureLocation(entity.skin.texture)
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
    private fun entityTick(event: EntityTickEvent.Post) {
        val entity = event.entity
        if (entity is IEntityAnimatable<*>) {
            entity.animController.tick()

            if (entity.isSpectator) entity.animController.stopAllAnimation()
        }
        entity.lastPosO = entity.position()
    }

    @SubscribeEvent
    private fun itemTick(event: ItemStackInventoryTickEvent) {
        val stack = event.stack
        stack.getCapability(SparkCapabilities.ITEM_ANIMATABLE, event.entity.level())?.apply {
            inventoryTick(event.entity)
            animController.tick()
        }
    }

    @SubscribeEvent
    private fun onBoneUpdate(event: BoneUpdateEvent) {
        val animatable = event.model.animatable
        animatable.onBoneUpdate(event)
    }

}
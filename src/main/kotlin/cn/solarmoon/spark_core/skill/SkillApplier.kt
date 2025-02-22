package cn.solarmoon.spark_core.skill

import cn.solarmoon.spark_core.entity.addRelativeMovement
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.common.SyncerTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.Event
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.entity.EntityEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object SkillApplier {

    @SubscribeEvent
    private fun onEntityLeave(event: EntityLeaveLevelEvent) {
        val entity = event.entity
        entity.allSkills.values.forEach { it.end() }
    }

    @SubscribeEvent
    private fun onEntityTick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        entity.allSkills.values.forEach {
            it.update()
        }
    }

    @SubscribeEvent
    private fun onHurt(event: LivingIncomingDamageEvent) {
        handle(event)
    }

    @SubscribeEvent
    private fun playerInput(event: MovementInputUpdateEvent) {
        handle(event)
    }

    private fun handle(event: EntityEvent) {
        event.entity.activeSkills.forEach {
            it.handleEvent(event)
        }
    }

}